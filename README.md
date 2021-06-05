# Filigree
A simple tool for remapping Minecraft mods from the official Mojang mappings to Fabric / Yarn mappings

## Usage
```
usage: filigree [OPTION]... [SOURCE_DIRECTORY]
 -c,--classpath <arg>    adds a classpath
 -C,--classpaths <arg>   imports a list of classpaths from a file
 -f,--force              force remap even if output path already exists
 -h,--help               display this message
 -m,--mc-version <arg>   the minecraft version to map against, defaults to
                         latest release
 -o,--output-dir <arg>   the output directory, defaults to
                         [SOURCE_DIRECTORY]_remapped
 -v,--version            print version information
 -y,--yarn-build <arg>   the build of yarn to remap to, defaults to latest
```

## How to get classpath
The `-C` option reads the provided text file line by line and adds the path on each
line to the classpath.

To produce such a file, add the following into the build.gradle for the project
you want to remap
```
task compileClasspath {
    doLast {
        ArrayList<String> list = new ArrayList<>()
        configurations.compileClasspath.each { list.add(it.toString()) }

        File extraFolder = new File( "${project.buildDir}/classpaths")
        if( !extraFolder.exists() ) {
            extraFolder.mkdirs()
        }

        new File("${project.buildDir}/classpaths/classpath.txt").text = list.join ("\n")
    }
}
```
and run `gradlew compileClasspath`

## Installation
Download the binaries from the GitHub releases or build the project with
```
./gradlew installDist
```
then copy the resulting files to the desired location and either symlink `bin/filigree`
to somewhere in your path (for instance `/usr/local/bin`) or add it to your path
