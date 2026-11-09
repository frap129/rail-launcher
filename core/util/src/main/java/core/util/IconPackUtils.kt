/*
 * Copyright 2024, Lawnchair
 * Copyright 2024, Rail Launcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package core.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber

private const val APP_FILTER_XML: String = "appfilter"
private const val DRAWABLE_XML: String = "drawable"
private const val DRAWABLE_ATTRIBUTE: String = "drawable"
private const val DRAWABLE_TYPE: String = "drawable"
private const val CATEGORY_TAG: String = "category"
private const val ITEM_TAG: String = "item"
private const val NAME_ATTRIBUTE: String = "name"

/** One heading of an Icon pack's drawable list; [name] is null when the pack lists none. */
data class DrawableCategory(val name: String?, val drawableNames: List<String>)

/**
 * The drawable categories [packPackageName] offers, in file order and without duplicates. Reads
 * drawable.xml from resources first and assets second; a pack without one falls back to the unique
 * drawable names in appfilter.xml as one untitled category. Names that do not resolve to a drawable
 * are dropped, as are categories left empty.
 */
fun getDrawableCategoriesFromPack(context: Context, packPackageName: String): List<DrawableCategory> {
    val listedCategories: List<DrawableCategory> = getXml(context, DRAWABLE_XML, packPackageName)
        ?.readDrawableCategories()
        .orEmpty()
    val categories: List<DrawableCategory> = if (listedCategories.any { category -> category.drawableNames.isNotEmpty() }) {
        listedCategories
    } else {
        val appFilterNames: List<String> = getXml(context, APP_FILTER_XML, packPackageName)
            ?.readAttributeValues(attribute = DRAWABLE_ATTRIBUTE)
            .orEmpty()
        listOf(DrawableCategory(name = null, drawableNames = appFilterNames))
    }
    val seenNames: MutableSet<String> = mutableSetOf()
    return categories.mapNotNull { category ->
        val resolvedNames: List<String> = category.drawableNames.distinct().filter { drawableName ->
            seenNames.add(drawableName) && hasDrawable(context, drawableName, packPackageName)
        }
        category.copy(drawableNames = resolvedNames).takeIf { resolved -> resolved.drawableNames.isNotEmpty() }
    }
}

private fun XmlPullParser.readDrawableCategories(): List<DrawableCategory> {
    val categories: MutableList<MutableDrawableCategory> = mutableListOf()
    var currentCategory: MutableDrawableCategory? = null
    forEachStartTag { parser ->
        when (parser.name) {
            CATEGORY_TAG -> {
                val category: MutableDrawableCategory = MutableDrawableCategory(name = parser.getAttributeValue(null, NAME_ATTRIBUTE))
                currentCategory = category
                categories.add(category)
            }

            ITEM_TAG -> parser.getAttributeValue(null, DRAWABLE_ATTRIBUTE)?.let { drawableName ->
                val category: MutableDrawableCategory = currentCategory ?: MutableDrawableCategory(name = null).also { created ->
                    currentCategory = created
                    categories.add(created)
                }
                category.drawableNames.add(drawableName)
            }
        }
    }
    return categories.map { category -> DrawableCategory(name = category.name, drawableNames = category.drawableNames.distinct()) }
}

private fun XmlPullParser.forEachStartTag(onStartTag: (XmlPullParser) -> Unit) {
    runCatching {
        while (next() != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                onStartTag(this)
            }
        }
    }.onFailure { error -> Timber.w(error, "Failed to read an icon pack's XML") }
}

private class MutableDrawableCategory(val name: String?, val drawableNames: MutableList<String> = mutableListOf())

private fun XmlPullParser.readAttributeValues(attribute: String): List<String> {
    val values: MutableList<String> = mutableListOf()
    forEachStartTag { parser -> parser.getAttributeValue(null, attribute)?.let { value -> values.add(value) } }
    return values
}

fun getIconsFromPack(context: Context, packPackageName: String): Set<Triple<ComponentName, String, Int>> {
    val icons: MutableSet<Triple<ComponentName, String, Int>> = mutableSetOf()
    getXml(context, APP_FILTER_XML, packPackageName)?.let { parseXml ->
        val compStart = "ComponentInfo{"
        val compStartLength = compStart.length
        val compEnd = "}"
        val compEndLength = compEnd.length
        try {
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType != XmlPullParser.START_TAG) continue
                val name = parseXml.name
                val isCalendar = name == "calendar"
                when (name) {
                    "item", "calendar" -> {
                        var componentName: String? = parseXml["component"]
                        val drawableName = parseXml[if (isCalendar) "prefix" else DRAWABLE_ATTRIBUTE]
                        if (componentName != null && drawableName != null) {
                            if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                                componentName = componentName.substring(compStartLength, componentName.length - compEndLength)
                            }
                            val parsed = ComponentName.unflattenFromString(componentName)
                            if (parsed != null) {
                                if (isCalendar) {
                                    // TODO Handle calendar icons
                                } else {
                                    icons.add(
                                        Triple(
                                            parsed,
                                            packPackageName,
                                            getDrawableId(context, drawableName, packPackageName)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    "dynamic-clock" -> {
                        // TODO: Handle dynamic clock icons
                    }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }
    return icons
}

operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)

@SuppressLint("DiscouragedApi")
fun getDrawableId(context: Context, drawableName: String, packPackageName: String): Int =
    context.packageManager.getResourcesForApplication(packPackageName).getIdentifier(drawableName, DRAWABLE_TYPE, packPackageName)

/** Whether [packPackageName] provides [drawableName], tolerating a missing pack. */
fun hasDrawable(context: Context, drawableName: String, packPackageName: String): Boolean =
    runCatching { getDrawableId(context, drawableName, packPackageName) != 0 }.getOrDefault(false)

fun getXml(context: Context, xmlName: String, packPackageName: String): XmlPullParser? {
    val res: Resources
    try {
        res = context.packageManager.getResourcesForApplication(packPackageName)
        @SuppressLint("DiscouragedApi")
        val resourceId = res.getIdentifier(xmlName, "xml", packPackageName)
        return if (0 != resourceId) {
            context.packageManager.getXml(packPackageName, resourceId, null)
        } else {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(res.assets.open("$xmlName.xml"), StandardCharsets.UTF_8.name())
            parser
        }
    } catch (_: PackageManager.NameNotFoundException) {
    } catch (_: IOException) {
    } catch (_: XmlPullParserException) {
    }
    return null
}
