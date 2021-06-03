@file:JvmName("Util")
package com.dimitriye.filigree

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.model.ClassMapping
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList

val moshi by lazy {
	Moshi.Builder()
		.addLast(KotlinJsonAdapterFactory())
		.build()
}

fun streamFileFromZippedURL(url: URL, path: String): InputStream {
	url.openStream().let {
		BufferedInputStream(it).let {
			ZipInputStream(it).let {
				var entry = it.nextEntry
				while (entry != null) {
					if (entry.name == path) {
						return it
					}
					entry = it.nextEntry
				}
			}
		}
	}

	throw IllegalArgumentException("URL $url does not contain file $path")
}

fun readClassPathFromFile(file: File): List<Path> {
	val out = ArrayList<Path>()
	file.forEachLine {
		out.add(Path.of(it))
	}
	return out
}

private fun cloneSubclasses(source: ClassMapping<*, *>, target: ClassMapping<*, *>) {
	source.innerClassMappings.parallelStream().forEach {
		val new = target.createInnerClassMapping(it.obfuscatedName, it.deobfuscatedName)

		cloneSubclasses(it, new)
	}
}

fun cloneClasses(mappings: MappingSet, target: MappingSet = MappingSet.create()): MappingSet {
	// WARNING: Relying on undocumented implementation details here
	// MappingSet *is* thread-safe, but that's undocumented, and
	// if this ever changes, the code will break
	mappings.topLevelClassMappings.parallelStream().forEach {
		val mapping = target.getOrCreateClassMapping(it.obfuscatedName)
		mapping.deobfuscatedName = it.deobfuscatedName

		cloneSubclasses(it, mapping)
	}

	return target
}