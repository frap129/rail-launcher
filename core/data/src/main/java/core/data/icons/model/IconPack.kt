package core.data.icons.model

sealed class IconPack(open val name: String, open val icon: Icon) {
    data class SystemIconPack(override val name: String, override val icon: Icon) : IconPack(name, icon)

    data class CustomIconPack(override val name: String, val packageName: String, override val icon: Icon, val icons: List<Icon.PackIcon>) :
        IconPack(name, icon)
}
