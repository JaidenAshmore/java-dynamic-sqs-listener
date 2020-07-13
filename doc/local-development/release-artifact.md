# Releasing an Artifact

This library is hosted using the [OSSRH](https://central.sonatype.org/pages/ossrh-guide.html) central repository. To successfully release the application
each module must be:

1. filled with all necessary information like project name, Source Code Management (SCM) information, etc
1. include JavaDoc and Sources
1. Be signed using GPG keys

The developer will release the plugin to a staging repository where it is checked against all of the checks above. If all of these pass it can be released
to the actual maven central repository. We use GitHub actions to actually perform the releasing so it does not need to be run on a specific machine.

## Releasing to Maven local

To publish to maven local so you can use it for other projects on your local machine you can run `gradle publishToMavenLocal` from the root directory.

## Releasing to Maven Central

To make the module available for consumption.

## Steps

1. Change the version in the [build.gradle.kts](../../build.gradle.kts) to the version you want to release, e.g. 4.0.0-SNAPSHOT to 4.0.0.
1. Commit this change
1. Create a new release from the branch, e.g. `4.x`, which should have that last commit that you just had
1. Creating this release will result in the [release github action](../../.github/workflows/release-v2.yml) triggering
1. Once this is successful make sure to make a new commit changing the version to the next SNAPSHOT version, e.g. 4.0.1-SNAPSHOT
