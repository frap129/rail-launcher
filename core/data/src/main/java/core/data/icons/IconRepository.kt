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

package core.data.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Xml
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.util.stateInBackground
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

const val SYSTEM_ICON_PACK_NAME: String = "System"
class IconRepository(val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    val defaultIconPack = IconPack.SystemIconPack(
        name = SYSTEM_ICON_PACK_NAME,
        icon = Icon.ApplicationIcon("com.android.egg")
    )
    private val iconPackIntents = listOf(
        Intent("com.novalauncher.THEME"),
        Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
        Intent("com.dlto.atom.launcher.THEME"),
        Intent("app.lawnchair.icons.THEMED_ICON"),
        Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
    )

    val iconPacks: StateFlow<Set<IconPack>> = flow<Set<IconPack>> {
        val iconPacks: MutableSet<IconPack> = iconPackIntents
            .flatMap { packageManager.queryIntentActivities(it, 0) }
            .mapTo(mutableSetOf<IconPack>()) { info ->
                IconPack.CustomIconPack(
                    info.loadLabel(packageManager).toString(),
                    info.activityInfo.packageName,
                    Icon.ApplicationIcon(info.activityInfo.packageName),
                    getIconsFromPack(info.activityInfo.packageName)
                )
            }.apply {
                add(defaultIconPack)
            }

        emit(iconPacks)
    }.stateInBackground(emptySet())

    private fun getIconsFromPack(packPackageName: String): List<Icon.PackIcon> {
        val icons: MutableList<Icon.PackIcon> = mutableListOf()
        getXml("appfilter", packPackageName)?.let { parseXml ->
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
                            val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
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
                                            Icon.PackIcon(
                                                parsed,
                                                packPackageName,
                                                getDrawableId(drawableName, packPackageName)
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

    private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)

    @SuppressLint("DiscouragedApi")
    private fun getDrawableId(drawableName: String, packPackageName: String) =
        packageManager.getResourcesForApplication(packPackageName).getIdentifier(drawableName, "drawable", packPackageName)

    private fun getXml(xmlName: String, packPackageName: String): XmlPullParser? {
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
                parser.setInput(res.assets.open("$xmlName.xml"), Xml.Encoding.UTF_8.toString())
                parser
            }
        } catch (_: PackageManager.NameNotFoundException) {
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }
}
