// a module added from a zip with no service behind it: a look, a player, a font, a glass, pages, menu rows, nothing that ever reaches the network

package com.kiyo.music.modules.local

import com.kiyo.music.module.AccountState
import com.kiyo.music.module.LookModule
import com.kiyo.music.module.ModuleArt
import com.kiyo.music.module.ModuleField
import com.kiyo.music.module.ModuleFields
import com.kiyo.music.module.ModuleHost
import com.kiyo.music.module.ModuleInfo
import com.kiyo.music.module.NO_ACCOUNT_LABEL
import com.kiyo.music.modules.skin.ModuleSkin
import com.kiyo.music.modules.skin.SkinGlass
import com.kiyo.music.modules.skin.SkinParser
import com.kiyo.music.modules.skin.SkinSource
import com.kiyo.music.modules.skin.appliedLook
import com.kiyo.music.modules.web.FieldRange
import com.kiyo.music.modules.web.FieldRanges
import com.kiyo.music.modules.web.ManifestSettings
import com.kiyo.music.modules.web.WebModuleManifest
import com.kiyo.music.modules.web.resolveModuleArt
import java.io.File

internal class LocalModule(private val manifest: WebModuleManifest, files: File?) : LookModule, ModuleArt, ModuleFields, SkinSource, FieldRanges {

    override val info = ModuleInfo(
        id = idFor(manifest),
        name = manifest.name.ifBlank { manifest.id },
        version = manifest.version.ifBlank { "1" },
        description = manifest.description.ifBlank { "Added from a file" },
        author = manifest.author,
    )

    override val skin: ModuleSkin? = SkinParser.parse(info.id, info.name, manifest.player, manifest.font, manifest.pages, files, base = null, manifest.actions, manifest.glass, manifest.players, manifest.bar, manifest.mini)

    override val icon: String? = resolveModuleArt(manifest.icon, files, base = null)

    override val banner: String? = resolveModuleArt(manifest.banner, files, base = null)

    private val settings = ManifestSettings(manifest)

    override val fields: List<ModuleField> get() = settings.fields

    override val ranges: Map<String, FieldRange> get() = settings.ranges

    override fun onFields(values: Map<String, String>) {
        settings.values = values
    }

    // the base look with the picked settings laid over it, so changing an option on the page changes the look
    override val look: Map<String, String> get() = appliedLook(settings.look(manifest.lookValues), skin)

    override val glass: SkinGlass? get() = settings.glass(skin?.glass)

    override fun attach(host: ModuleHost) = Unit

    // there's no server to ask, it lives on this phone
    override suspend fun verifyAccount(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    companion object {
        // local. in front, so a file can never take the place of a built-in module or one added from a link
        fun idFor(manifest: WebModuleManifest): String =
            "local." + manifest.id.ifBlank { manifest.name }.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    }
}
