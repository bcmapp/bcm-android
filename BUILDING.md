Building BCM
===============

Basics
------

BCM uses [Gradle](http://gradle.org) to build the project and to maintain
dependencies.  However, you needn't install it yourself; the
"gradle wrapper" `gradlew`, mentioned below, will do that for you.

Building BCM
---------------

The following steps should help you (re)build BCM from the command line.

1. Checkout the BCM-Android project source with the command:

        git clone https://github.com/bcmapp/bcm-android.git

2. Make sure you have the [Android SDK](https://developer.android.com/sdk/index.html) installed.
3. Ensure that the following packages are installed from the Android SDK manager:
    * Android SDK Build Tools (see buildToolsVersion in build.gradle)
    * SDK Platform (All API levels)
    * Android Support Repository
    * Google Repository
4. Create a local.properties file at the root of your source checkout and add an sdk.dir entry to it.  For example:

        sdk.dir=/Application/android-sdk-macosx

5. Using Java 8 

6. Execute Gradle:

        ./gradlew build


Setting up a development environment
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Install Android Studio.
2. Open Android Studio. On a new installation, the Quickstart panel will appear. If you have open projects, close them using "File > Close Project" to see the Quickstart panel.
3. From the Quickstart panel, choose "Configure" then "SDK Manager".
4. In the SDK Tools tab of the SDK Manager, make sure that the "Android Support Repository" is installed, and that the latest "Android SDK build-tools" are installed. Click "OK" to return to the Quickstart panel.
5. From the Quickstart panel, choose "Checkout from Version Control" then "git".
6. Paste the URL for the im-android project when prompted (https://github.com/bcmapp/bcm-android.git).
7. Android studio should detect the presence of a project file and ask you whether to open it. Click "yes".
9. Default config options should be good enough.
10. Project initialisation and build should proceed.


About BCM Project
-----------------
├── **app** *Application entry module, responsible for the initialization of the basic services and capabilities of the app*<br/>
├── **utility** *Application's basic tool library*<br/>
├── **common** *Application's common function logic, implementation of common components*<br/>
├── **chats** *Application chat and group chat interaction and function logic implementation*<br/>
├── **contacts** *Address book interaction, function logic implementation, and user data management for applications*<br/>
├── **login**  *Application login logic implementation*<br/>
├── **me** *Interactive implementation of registe and login*<br/>
├── **netswitchy** *Agent support implementation(eg:ss,ssr,obsf4,socks5)*<br/>
├── **adhoc-client** *Networkless communication interaction and logic implementation*<br/>
├── **umeng** *offline push module*<br/>
├── **appsflyer**<br/>
├── **wallet** *Wallet interaction and function implementation*<br/>
├── **thirdpart**<br/>
│   ├── **bitcoin** *bitcoin sdk*<br/>
│   ├── **ethereum** *eth sdk*<br/>
│   ├── **fcm_analytics_plugin** *disable fcm analytics*<br/>
│   ├── **router** *Module function routing implementation*<br/>
│   ├── **signal-protocol** *signal protocol sdk for private chat*<br/>

