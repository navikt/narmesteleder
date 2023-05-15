# narmesteleder
This project contains the application code and infrastructure for narmesteleder

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Docker

### :scroll: Prerequisites
* JDK 17
Make sure you have the Java JDK 17 installed
You can check which version you have installed using this command:
``` shell
java -version
```

## FlowChart
This the high level flow of the application
```mermaid
  graph LR
      narmesteleder --- Azure-AD
      narmesteleder <--> id1[(Database)]
      narmesteleder <--> PDL
      A([pdl.aktor-v2]) --> narmesteleder
      narmesteleder <--> aareg
      B([teamsykmelding.syfo-narmesteleder]) --> narmesteleder
      narmesteleder --> C([teamsykmelding.syfo-nl-request])
      narmesteleder --> D([teamsykmelding.syfo-narmesteleder-leesah])
      narmesteleder <--> narmesteleder-redis;
```

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. 
It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

``` shell
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run
``` shell
./gradlew shadowJar
```
or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as
``` shell
docker build -t narmesteleder .
```

#### Running a docker image
``` shell
docker run --rm -it -p 8080:8080 narmesteleder
```

### Upgrading the gradle wrapper

Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` shell
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/narmesteleder/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
