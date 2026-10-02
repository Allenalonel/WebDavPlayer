/*
 * Copyright (C) 2026 WebDavPlayer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/log.h>
#include <jni.h>
#include <new>
#include <stdlib.h>
#include <string.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
}

#define LOG_TAG "ffmpeg_demuxer"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))

#define AVIO_BUFFER_SIZE 32768
#define ASF_MAX_STREAMS 128

// Internal ASF structures matching FFmpeg libavformat asfdec_o.c
struct ASFPacket_O {
    AVPacket avpkt;
    int64_t dts;
    uint32_t frame_num;
    int flags;
    int data_size;
    int duration;
    int size_left;
    uint8_t stream_index;
};

struct ASFStream_O {
    uint8_t stream_index;
    int index;
    int type;
    int indexed;
    int8_t span;
    uint16_t virtual_pkt_len;
    uint16_t virtual_chunk_len;
    int16_t lang_idx;
    ASFPacket_O pkt;
};

struct ASFStreamData_O {
    char langs[32];
    AVDictionary *asf_met;
    AVRational aspect_ratio;
};

struct ASFContext_O {
    int data_reached;
    int is_simple_index;
    int is_header;

    uint64_t preroll;
    uint64_t nb_packets;
    uint32_t packet_size;
    int64_t send_time;
    int duration;

    uint32_t b_flags;
    uint32_t prop_flags;

    uint64_t data_size;
    uint64_t unknown_size;

    int64_t offset;

    int64_t data_offset;
    int64_t first_packet_offset;
    int64_t unknown_offset;
    int in_asf_read_unknown;

    ASFStream_O *asf_st[ASF_MAX_STREAMS];
    ASFStreamData_O asf_sd[ASF_MAX_STREAMS];
    int nb_streams;

    int stream_index;

    uint64_t sub_header_offset;
    int64_t sub_dts;
    uint8_t dts_delta;
    uint32_t packet_size_internal;
    int64_t packet_offset;
    uint32_t pad_len;
    uint32_t rep_data_len;

    uint64_t sub_left;
    unsigned int nb_sub;
    uint16_t mult_sub_len;
    uint64_t nb_mult_left;
    int return_subpayload;
    enum {
        PARSE_PACKET_HEADER,
        READ_SINGLE,
        READ_MULTI,
        READ_MULTI_SUB
    } state;
};

static void asf_o_reset_packet_state_custom(AVFormatContext *s, int64_t target_position) {
    if (!s || !s->priv_data) return;
    ASFContext_O *asf = reinterpret_cast<ASFContext_O *>(s->priv_data);

    asf->state             = ASFContext_O::PARSE_PACKET_HEADER;
    asf->offset            = 0;
    asf->return_subpayload = 0;
    asf->sub_left          = 0;
    asf->sub_header_offset = 0;
    asf->packet_offset     = target_position;
    asf->pad_len           = 0;
    asf->rep_data_len      = 0;
    asf->dts_delta         = 0;
    asf->mult_sub_len      = 0;
    asf->nb_mult_left      = 0;
    asf->nb_sub            = 0;
    asf->prop_flags        = 0;
    asf->sub_dts           = 0;

    for (int i = 0; i < asf->nb_streams; i++) {
        if (asf->asf_st[i]) {
            ASFPacket_O *pkt = &asf->asf_st[i]->pkt;
            pkt->size_left = 0;
            pkt->data_size = 0;
            pkt->duration  = 0;
            pkt->flags     = 0;
            pkt->dts       = 0;
            av_packet_unref(&pkt->avpkt);
            av_init_packet(&pkt->avpkt);
        }
    }
}

struct NativeDemuxerContext {
    AVFormatContext *fmt_ctx = nullptr;
    AVIOContext *avio_ctx = nullptr;
    unsigned char *avio_buffer = nullptr;

    JNIEnv *current_env = nullptr;
    jobject current_input = nullptr;
    jmethodID mid_read = nullptr;
    jmethodID mid_get_position = nullptr;
    jmethodID mid_get_length = nullptr;
    jmethodID mid_skip_fully = nullptr;
    jbyteArray java_io_buffer = nullptr;

    int audio_stream_index = -1;
    AVRational time_base = {1, 1000};
    int64_t duration_us = 0;
    int sample_rate = 0;
    int channels = 0;
    int bitrate = 0;
    int block_align = 0;
    int codec_id = 0;
    char codec_name[32] = {0};

    uint8_t *extradata = nullptr;
    int extradata_size = 0;

    bool eof = false;
};

static int read_packet_callback(void *opaque, uint8_t *buf, int buf_size) {
    NativeDemuxerContext *ctx = static_cast<NativeDemuxerContext *>(opaque);
    if (!ctx || !ctx->current_env || !ctx->current_input) {
        return AVERROR_EXTERNAL;
    }
    JNIEnv *env = ctx->current_env;
    if (buf_size <= 0) return 0;

    int to_read = buf_size < AVIO_BUFFER_SIZE ? buf_size : AVIO_BUFFER_SIZE;
    jint bytes_read = env->CallIntMethod(
        ctx->current_input,
        ctx->mid_read,
        ctx->java_io_buffer,
        0,
        to_read
    );

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return AVERROR(EIO);
    }

    if (bytes_read < 0) {
        ctx->eof = true;
        return AVERROR_EOF;
    }
    if (bytes_read == 0) {
        return AVERROR(EAGAIN);
    }

    env->GetByteArrayRegion(ctx->java_io_buffer, 0, bytes_read, reinterpret_cast<jbyte *>(buf));
    return bytes_read;
}

static int64_t seek_callback(void *opaque, int64_t offset, int whence) {
    NativeDemuxerContext *ctx = static_cast<NativeDemuxerContext *>(opaque);
    if (!ctx || !ctx->current_env || !ctx->current_input) {
        return -1;
    }
    JNIEnv *env = ctx->current_env;

    if (whence == AVSEEK_SIZE) {
        jlong length = env->CallLongMethod(ctx->current_input, ctx->mid_get_length);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;
        }
        return length > 0 ? (int64_t)length : -1;
    }

    if (whence == SEEK_CUR) {
        if (offset == 0) {
            jlong pos = env->CallLongMethod(ctx->current_input, ctx->mid_get_position);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return -1;
            }
            return (int64_t)pos;
        } else if (offset > 0 && ctx->mid_skip_fully) {
            // Support forward skipping (e.g. packet padding or metadata)
            env->CallVoidMethod(ctx->current_input, ctx->mid_skip_fully, (jint)offset);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return -1;
            }
            jlong new_pos = env->CallLongMethod(ctx->current_input, ctx->mid_get_position);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return -1;
            }
            return (int64_t)new_pos;
        }
    }

    if (whence == SEEK_SET && ctx->mid_skip_fully) {
        jlong current_pos = env->CallLongMethod(ctx->current_input, ctx->mid_get_position);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;
        }
        if (offset >= current_pos) {
            jlong delta = offset - current_pos;
            if (delta > 0) {
                env->CallVoidMethod(ctx->current_input, ctx->mid_skip_fully, (jint)delta);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    return -1;
                }
            }
            jlong new_pos = env->CallLongMethod(ctx->current_input, ctx->mid_get_position);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return -1;
            }
            return (int64_t)new_pos;
        }
    }

    return -1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeInit(
    JNIEnv *env, jobject thiz) {
    NativeDemuxerContext *ctx = new (std::nothrow) NativeDemuxerContext();
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeOpen(
    JNIEnv *env, jobject thiz, jlong handle, jobject input) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    if (!ctx) return -1;

    jclass input_clazz = env->GetObjectClass(input);
    ctx->mid_read = env->GetMethodID(input_clazz, "read", "([BII)I");
    ctx->mid_get_position = env->GetMethodID(input_clazz, "getPosition", "()J");
    ctx->mid_get_length = env->GetMethodID(input_clazz, "getLength", "()J");
    ctx->mid_skip_fully = env->GetMethodID(input_clazz, "skipFully", "(I)V");

    if (!ctx->mid_read || !ctx->mid_get_position || !ctx->mid_get_length) {
        LOGE("Failed to find ExtractorInput methods.");
        return -2;
    }

    if (!ctx->java_io_buffer) {
        jbyteArray local_arr = env->NewByteArray(AVIO_BUFFER_SIZE);
        ctx->java_io_buffer = static_cast<jbyteArray>(env->NewGlobalRef(local_arr));
        env->DeleteLocalRef(local_arr);
    }

    ctx->current_env = env;
    ctx->current_input = input;

    ctx->avio_buffer = static_cast<unsigned char *>(av_malloc(AVIO_BUFFER_SIZE));
    if (!ctx->avio_buffer) {
        LOGE("Failed to allocate avio buffer.");
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return -3;
    }

    ctx->avio_ctx = avio_alloc_context(
        ctx->avio_buffer,
        AVIO_BUFFER_SIZE,
        0,
        ctx,
        read_packet_callback,
        nullptr,
        nullptr
    );
    if (!ctx->avio_ctx) {
        LOGE("Failed to allocate AVIOContext.");
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return -4;
    }

    ctx->fmt_ctx = avformat_alloc_context();
    if (!ctx->fmt_ctx) {
        LOGE("Failed to allocate AVFormatContext.");
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return -5;
    }
    ctx->fmt_ctx->pb = ctx->avio_ctx;
    ctx->fmt_ctx->flags |= AVFMT_FLAG_CUSTOM_IO;

    // Prefer modern 'asf_o' demuxer which contains complete asf_deinterleave state machine
    AVInputFormat *input_fmt = av_find_input_format("asf_o");
    if (!input_fmt) {
        LOGI("asf_o demuxer not found, falling back to asf");
        input_fmt = av_find_input_format("asf");
    } else {
        LOGI("Successfully selected modern asf_o (de-interleaving) demuxer");
    }

    int ret = avformat_open_input(&ctx->fmt_ctx, nullptr, input_fmt, nullptr);
    if (ret < 0) {
        char err_buf[128];
        av_strerror(ret, err_buf, sizeof(err_buf));
        LOGE("avformat_open_input failed: %s (%d)", err_buf, ret);
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return ret;
    }

    ret = avformat_find_stream_info(ctx->fmt_ctx, nullptr);
    if (ret < 0) {
        LOGE("avformat_find_stream_info failed: %d", ret);
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return ret;
    }

    ctx->audio_stream_index = av_find_best_stream(
        ctx->fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (ctx->audio_stream_index < 0) {
        LOGE("No audio stream found in ASF container.");
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return -6;
    }

    AVStream *st = ctx->fmt_ctx->streams[ctx->audio_stream_index];
    ctx->time_base = st->time_base;
    if (st->duration > 0 && ctx->time_base.den > 0) {
        ctx->duration_us = av_rescale_q(st->duration, ctx->time_base, AV_TIME_BASE_Q);
    } else if (ctx->fmt_ctx->duration > 0) {
        ctx->duration_us = ctx->fmt_ctx->duration;
    }

    AVCodecParameters *par = st->codecpar;
    if (par) {
        ctx->sample_rate = par->sample_rate;
        ctx->channels = par->channels;
        ctx->bitrate = (int)par->bit_rate;
        ctx->block_align = par->block_align;
        ctx->codec_id = par->codec_id;

        const char *name = avcodec_get_name(par->codec_id);
        if (name) {
            strncpy(ctx->codec_name, name, sizeof(ctx->codec_name) - 1);
        }

        if (par->extradata_size > 0 && par->extradata) {
            ctx->extradata_size = par->extradata_size;
            ctx->extradata = static_cast<uint8_t *>(av_malloc(par->extradata_size));
            if (ctx->extradata) {
                memcpy(ctx->extradata, par->extradata, par->extradata_size);
            }
        }
    }

    LOGI("Native ASF demuxer opened: stream=%d, codec=%s, sr=%d, ch=%d, align=%d, durUs=%lld",
         ctx->audio_stream_index, ctx->codec_name, ctx->sample_rate, ctx->channels,
         ctx->block_align, (long long)ctx->duration_us);

    ctx->current_env = nullptr;
    ctx->current_input = nullptr;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetSampleRate(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return ctx ? ctx->sample_rate : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetChannelCount(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return ctx ? ctx->channels : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetBitrate(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return ctx ? ctx->bitrate : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetBlockAlign(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return ctx ? ctx->block_align : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetDurationUs(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return ctx ? ctx->duration_us : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetCodecName(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    return (ctx && ctx->codec_name[0]) ? env->NewStringUTF(ctx->codec_name) : nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeGetExtraData(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    if (!ctx || !ctx->extradata || ctx->extradata_size <= 0) return nullptr;
    jbyteArray arr = env->NewByteArray(ctx->extradata_size);
    env->SetByteArrayRegion(arr, 0, ctx->extradata_size, reinterpret_cast<jbyte *>(ctx->extradata));
    return arr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeReadFrame(
    JNIEnv *env, jobject thiz, jlong handle, jobject input,
    jbyteArray output_array, jlongArray out_meta) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    if (!ctx || !ctx->fmt_ctx) return -1;

    ctx->current_env = env;
    ctx->current_input = input;

    jsize dst_capacity = env->GetArrayLength(output_array);
    if (dst_capacity <= 0) {
        ctx->current_env = nullptr;
        ctx->current_input = nullptr;
        return -1;
    }

    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = nullptr;
    pkt.size = 0;

    int result = -1;
    while (true) {
        int ret = av_read_frame(ctx->fmt_ctx, &pkt);
        if (ret < 0) {
            if (ret == AVERROR(EAGAIN)) {
                result = 2; // Need more data
            } else if (ret == AVERROR_EOF || ctx->eof) {
                result = 1; // EOF
            } else {
                result = -1; // Non-fatal Error
            }
            break;
        }

        if (pkt.stream_index == ctx->audio_stream_index) {
            if (pkt.size > dst_capacity) {
                LOGE("Packet size %d exceeds array capacity %d", pkt.size, dst_capacity);
                av_packet_unref(&pkt);
                result = -1;
                break;
            }

            env->SetByteArrayRegion(output_array, 0, pkt.size, reinterpret_cast<jbyte *>(pkt.data));

            int64_t raw_pts = AV_NOPTS_VALUE;
            if (pkt.pts != AV_NOPTS_VALUE) {
                raw_pts = pkt.pts;
            } else if (pkt.dts != AV_NOPTS_VALUE) {
                raw_pts = pkt.dts;
            }

            // Normalization for ASF 32-bit unsigned timestamp underflow:
            // In ASF files, preroll subtraction (send_time - preroll) on unsigned 32-bit values
            // causes underflow when send_time < preroll (e.g. 0 - 3100ms -> 4294964196ms ≈ 1193 hours).
            // Any timestamp > 0x80000000LL (24.8 days in milliseconds) is an underflowed negative timestamp.
            if (raw_pts != AV_NOPTS_VALUE && raw_pts > 0x80000000LL) {
                int32_t signed_pts = static_cast<int32_t>(raw_pts);
                raw_pts = signed_pts < 0 ? 0 : signed_pts;
            }

            int64_t pts_us = 0;
            if (raw_pts != AV_NOPTS_VALUE && raw_pts >= 0 && ctx->time_base.den > 0) {
                pts_us = av_rescale_q(raw_pts, ctx->time_base, AV_TIME_BASE_Q);
            }
            if (pts_us < 0) {
                pts_us = 0;
            }

            jlong meta[3];
            meta[0] = pkt.size;
            meta[1] = pts_us;
            meta[2] = (pkt.flags & AV_PKT_FLAG_KEY) ? 1 : 0;

            env->SetLongArrayRegion(out_meta, 0, 3, meta);

            av_packet_unref(&pkt);
            result = 0; // Success
            break;
        }

        av_packet_unref(&pkt);
    }

    ctx->current_env = nullptr;
    ctx->current_input = nullptr;
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeSeek(
    JNIEnv *env, jobject thiz, jlong handle, jlong position, jlong time_us) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    if (!ctx || !ctx->fmt_ctx) return -1;

    if (ctx->avio_ctx) {
        avio_flush(ctx->avio_ctx);
        ctx->avio_ctx->pos = position;
        ctx->avio_ctx->eof_reached = 0;
        ctx->avio_ctx->error = 0;
    }

    // Reset asf_o packet parsing state machine to start parsing brand new packet at targetPos
    asf_o_reset_packet_state_custom(ctx->fmt_ctx, position);

    ctx->eof = false;
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_webdav_player_data_player_AsfExtractor_nativeRelease(
    JNIEnv *env, jobject thiz, jlong handle) {
    NativeDemuxerContext *ctx = reinterpret_cast<NativeDemuxerContext *>(handle);
    if (!ctx) return;

    if (ctx->extradata) {
        av_free(ctx->extradata);
        ctx->extradata = nullptr;
    }

    if (ctx->java_io_buffer) {
        env->DeleteGlobalRef(ctx->java_io_buffer);
        ctx->java_io_buffer = nullptr;
    }

    if (ctx->fmt_ctx) {
        avformat_close_input(&ctx->fmt_ctx);
        ctx->fmt_ctx = nullptr;
    }

    if (ctx->avio_ctx) {
        if (ctx->avio_ctx->buffer) {
            av_free(ctx->avio_ctx->buffer);
            ctx->avio_ctx->buffer = nullptr;
        }
        av_free(ctx->avio_ctx);
        ctx->avio_ctx = nullptr;
    }

    delete ctx;
}
