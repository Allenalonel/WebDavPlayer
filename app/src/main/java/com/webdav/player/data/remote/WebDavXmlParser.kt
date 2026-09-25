package com.webdav.player.data.remote

import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory

object WebDavXmlParser {

    fun parseMultistatus(
        xml: String,
        serverPathPrefix: String,
        requestedPath: String
    ): RemoteDirectory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))

        val subDirectories = mutableListOf<RemoteDirectory>()
        val files = mutableListOf<RemoteFile>()

        val normalizedRequestedPath = normalizePath(requestedPath, isDirectory = true)
        val serverPrefix = serverPathPrefix.trim().trimEnd('/')

        val responseNodes = doc.getElementsByTagNameNS("*", "response")
        val numResponses = if (responseNodes.length > 0) {
            responseNodes.length
        } else {
            doc.getElementsByTagName("response").length
        }

        val responses = (0 until numResponses).map { i ->
            if (responseNodes.length > 0) responseNodes.item(i) as Element
            else doc.getElementsByTagName("response").item(i) as Element
        }

        var dirName: String? = null

        for (responseEl in responses) {
            val hrefRaw = getChildTextByLocalName(responseEl, "href") ?: continue
            val decodedPath = decodeAndExtractPath(hrefRaw)
            val relativePath = makeRelative(decodedPath, serverPrefix)

            val isCollection = checkIsCollection(responseEl, hrefRaw)

            if (isCollection) {
                val normalizedPath = normalizePath(relativePath, isDirectory = true)
                if (normalizedPath.equals(normalizedRequestedPath, ignoreCase = true) ||
                    normalizedPath.trimEnd('/') == normalizedRequestedPath.trimEnd('/')
                ) {
                    val extractedName = normalizedPath.trimEnd('/').substringAfterLast('/')
                    if (extractedName.isNotBlank()) {
                        dirName = extractedName
                    }
                    continue
                }

                val folderName = normalizedPath.trimEnd('/').substringAfterLast('/')
                if (folderName.isNotBlank()) {
                    subDirectories.add(
                        RemoteDirectory(
                            path = normalizedPath,
                            name = folderName
                        )
                    )
                }
            } else {
                val normalizedFilePath = normalizePath(relativePath, isDirectory = false)
                val fileName = normalizedFilePath.substringAfterLast('/')
                if (fileName.isNotBlank()) {
                    val contentLength = getChildTextByLocalName(responseEl, "getcontentlength")?.toLongOrNull() ?: 0L
                    val lastModified = getChildTextByLocalName(responseEl, "getlastmodified")
                    val contentType = getChildTextByLocalName(responseEl, "getcontenttype")

                    files.add(
                        RemoteFile(
                            name = fileName,
                            path = normalizedFilePath,
                            size = contentLength,
                            lastModified = lastModified,
                            contentType = contentType
                        )
                    )
                }
            }
        }

        val finalDirName = dirName
            ?: normalizedRequestedPath.trimEnd('/').substringAfterLast('/').ifBlank { "/" }

        return RemoteDirectory(
            path = normalizedRequestedPath,
            name = finalDirName,
            subDirectories = subDirectories.sortedBy { it.name.lowercase() },
            files = files.sortedBy { it.name.lowercase() }
        )
    }

    private fun decodeAndExtractPath(href: String): String {
        val pathOnly = if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            try {
                URI(href).rawPath ?: href.substringAfter("://").substringAfter("/")
            } catch (e: Exception) {
                href.substringAfter("://").substringAfter("/")
            }
        } else {
            href
        }
        return try {
            URLDecoder.decode(pathOnly, "UTF-8")
        } catch (e: Exception) {
            pathOnly
        }
    }

    private fun makeRelative(path: String, serverPrefix: String): String {
        var cleanPath = path
        if (serverPrefix.isNotEmpty() && cleanPath.startsWith(serverPrefix)) {
            cleanPath = cleanPath.substring(serverPrefix.length)
        }
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/$cleanPath"
        }
        return cleanPath
    }

    private fun normalizePath(path: String, isDirectory: Boolean): String {
        var p = path.replace('\\', '/')
        if (!p.startsWith("/")) p = "/$p"
        if (isDirectory && !p.endsWith("/")) p = "$p/"
        return p
    }

    private fun checkIsCollection(responseEl: Element, rawHref: String): Boolean {
        val resourceTypeNodes = findElementsByLocalName(responseEl, "resourcetype")
        for (rtNode in resourceTypeNodes) {
            val collectionNodes = findElementsByLocalName(rtNode, "collection")
            if (collectionNodes.isNotEmpty()) {
                return true
            }
        }
        return rawHref.endsWith("/") && !rawHref.substringAfterLast('/').contains('.')
    }

    private fun getChildTextByLocalName(element: Element, localName: String): String? {
        val matching = findElementsByLocalName(element, localName)
        return matching.firstOrNull()?.textContent?.trim()
    }

    private fun findElementsByLocalName(element: Element, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        collectElementsByLocalName(element, localName, result)
        return result
    }

    private fun collectElementsByLocalName(node: Node, localName: String, result: MutableList<Element>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val nodeLocalName = child.localName ?: child.nodeName.substringAfterLast(':')
                if (nodeLocalName.equals(localName, ignoreCase = true)) {
                    result.add(child)
                }
                collectElementsByLocalName(child, localName, result)
            }
        }
    }
}
