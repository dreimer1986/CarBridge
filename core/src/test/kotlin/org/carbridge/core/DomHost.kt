package org.carbridge.core

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Same shape as the DOM adapter in CarBridge Patches; descriptors are only recorded. */
internal class DomHost(val doc: Document) : ManifestHost {
    val descriptors = mutableListOf<String>()
    val descriptorUses = mutableMapOf<String, List<String>>()
    override val manifest = DomElement(doc.documentElement)
    override val application = manifest.children("application").single()

    override fun hasPermission(name: String) =
        manifest.children("uses-permission").any { it.getString(CarPatch.NAME) == name }

    override fun addPermission(name: String) {
        val el = doc.createElement("uses-permission")
        el.setAttribute("android:name", name)
        doc.documentElement.insertBefore(el, (application as DomElement).el)
    }

    override fun addAutomotiveDescriptor(name: String, vararg uses: String) {
        descriptors += name
        descriptorUses[name] = uses.toList()
    }

    override fun log(message: String) = Unit

    companion object {
        fun parse(xml: String) =
            DomHost(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream()))
    }
}

internal class DomElement(val el: Element) : ManifestElement {
    override val tag: String get() = el.tagName
    override fun children(tag: String): List<ManifestElement> {
        val nodes = el.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }.map(::DomElement)
    }

    override fun getString(attr: AndroidAttr) = el.getAttribute("android:${attr.name}").ifEmpty { null }
    override fun getInt(attr: AndroidAttr): Int? = getString(attr)?.let { it.toIntOrNull() ?: attr.enumValues[it] }
    override fun setString(attr: AndroidAttr, value: String) = el.setAttribute("android:${attr.name}", value)
    override fun setBoolean(attr: AndroidAttr, value: Boolean) = setString(attr, value.toString())
    override fun setInt(attr: AndroidAttr, value: Int) = setString(attr, value.toString())
    override fun setEnum(attr: AndroidAttr, value: String) = setString(attr, value)
    override fun setResource(attr: AndroidAttr, type: String, name: String) = setString(attr, "@$type/$name")
    override fun remove(attr: AndroidAttr) = el.removeAttribute("android:${attr.name}")
    override fun append(tag: String): ManifestElement = DomElement(el.ownerDocument.createElement(tag).also(el::appendChild))
    override fun removeChild(child: ManifestElement) {
        el.removeChild((child as DomElement).el)
    }
}
