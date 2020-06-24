# Local Development Guide - Setting up IntelliJ

This guide contains some steps for setting up your local environment to work with this library. Note that this will
assume that you are using IntelliJ for your IDE.

## Steps

*Note these steps were written using IntelliJ 2017.1 so the steps may be different for you.*

1. Open the project in IntelliJ.
1. Install the Lombok Plugin in IntelliJ.
```IntelliJ IDEA -> Preferences -> Plugins -> Browse Repositories -> Search Lombok -> Install```
1. Install the Checkstyle Plugin in IntelliJ
```IntelliJ IDEA -> Preferences -> Plugins -> Browse Repositories -> Search Checkstyle -> Install```
1. Enable Annotation Processing
```Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable Annotation Processing```
1. Add the checkstyle as the code style in the project.
    1. Go to the Checkstyle screen
    ```IntelliJ IDEA -> Preferences -> Other Settings -> Checkstyle```
    1. Update the Checkstyle version to `6.19`
    1. Load the Checkstyle in this project by clicking the `+` and choosing
    [configuration/checkstyle/google_6_18_checkstyle.xml](../../configuration/checkstyle/google_6_18_checkstyle.xml)
    1. Update the code style for Java to use this checkstyle file as well:
    ```IntelliJ IDEA -> Preferences -> Editor -> Code Style -> Java -> Code next to Schema -> Import Schema -> Checkstyle configuration```
1. Disable JavaDoc formatting. I could never get it working correctly with the checkstyle above.
```IntelliJ IDEA -> Preferences -> Editor -> Code Style -> Java -> JavaDoc -> Disable JavaDoc Styling```
1. For some reason the imports in the code style doesn't work correctly. Updating the Import layout for the Java
code style:
```IntelliJ IDEA -> Preferences -> Editor -> Code Style -> Java -> Imports``` to be

```text
import static all other imports
<blank line>
import all other imports
<blank line>
import java.*
import javax.*
<blank line>
```
