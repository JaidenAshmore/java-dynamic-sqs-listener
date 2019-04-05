# Releasing an Artifact
This guide contains the steps to set up the local environment for releasing an artifact to the public Maven repository.

## Steps
1. Set up the maven server credentials by updating the `~/.m2/settings.xml` file to include the GPG credentials and
the server for the maven repository.
    ```xml
    <settings>
      <profiles>
        <profile>
          <id>java-dynamic-sqs-listener</id>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
          <properties>
            <gpg.keyname>${GPG_KEY_HERE}</gpg.keyname>
            <gpg.passphrase>${GPG_PASSWORD_WITH_MVN_ENCRYPTION}</gpg.passphrase>
          </properties>
        </profile>
      </profiles>
      <servers>
        <server>
          <id>ossrh</id>
          <username>Ashmore</username>
          <password>${OSS_JIRA_PASSWORD}</password>
        </server>
      </servers>
    </settings>
    ```
1. Set the GPG TTY: `export GPG_TTY=$(tty)`
1. If you haven't uploaded your GPG credentials you can do so by going:
    ```gpg --keyserver hkp://pool.sks-keyservers.net --send-keys ${KEY_ID}```
    ```gpg --keyserver hkp://keyserver.ubuntu.com --send-keys ${KEY_ID}```
1. Run the Maven release process: `mvn release:prepare release:perform`
