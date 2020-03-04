## Dependencies Check

A Gradle plugin that can hint user the same Gradle dependent library does not have the same version.


## Usage

~~~
repositories {
    google()
    jcenter()
    maven { url 'https://jitpack.io' }
}
~~~

~~~
dependencies {
    classpath 'com.android.tools.build:gradle:4.0.0'
    classpath 'com.github.succlz123:gradle-dependencies-check:0.0.1'
}
~~~

~~~
apply plugin: 'org.succlz123.dependencies-check'
~~~

## Execute

~~~
./gradlew checkGradleDependencies
~~~

## Config

~~~
// to show the exception to abort or only show the repetitive version log tips
dependenciesExtension {
    showException = true
}
~~~

## Error Tips

~~~
> checkGradleDependencies Failed: com.google.zxing:core
  	 version: 3.3.3
  		found: app:com.google.zxing:core:3.3.3
  	 version: 3.3.0
  		found: app-test:com.google.zxing:core:3.3.0
~~~
