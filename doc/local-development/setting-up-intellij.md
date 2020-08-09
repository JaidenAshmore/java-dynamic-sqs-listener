# Local Development Guide - Setting up IntelliJ

This guide contains some steps for setting up your local environment to work with this library. Note that this will
assume that you are using IntelliJ for your IDE.

## Steps

_Note these steps are for IntelliJ 2017.1, so the steps may be different for you._

1. Open the project in IntelliJ.
1. Install the Lombok Plugin in IntelliJ.
   `IntelliJ IDEA -> Preferences -> Plugins -> Browse Repositories -> Search Lombok -> Install`
1. Install the Prettier Plugin in IntelliJ
   `IntelliJ IDEA -> Preferences -> Plugins -> Browse Repositories -> Search Prettier -> Install`
1. Configure the prettier plugin to run on all files `{**/*,*}.*` and to run on code format
1. Enable Annotation Processing
   `Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable Annotation Processing`
1. Update the Import layout for the Java code style:
   `IntelliJ IDEA -> Preferences -> Editor -> Code Style -> Java -> Imports` to be

```text
import static all other imports
<blank line>
import all other imports
<blank line>
```
