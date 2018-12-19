# Bluetooth GATT Server Sample (Java)

This application provides gateway between BLE devices thermometers and control device for
gas heater. It is based on Android Things BLE example (BluetoothGATTServerJava) and changed multiple
times by trying to find best Cloud IoT provider.
Right now it is runing on Android Things - Raspberry Pi based hardware and publishes data to
Amazon IoT.

## Pre-requisites

- Android Things compatible board beeing Raspberry <Pi
- Android device running Android 4.3 (API 18) or later
- Android Studio 2.2+

## Getting Started


## Preparing keystore

For beeing able to connect to AWS IoT certificate and private key have to be generated in
AWS IoT console. Those files are then downloaded to local disk and from those Java keystore is
created. Use following commands:

Using an off-device keystore (optional)
This section provides information about how to use an AWS IoT certificate and private key which were
created off of the device. The following instructions walk through the process of creating a keystore
which can be placed on the filesystem of the device and accessed by the Android SDK.

The keytool command does not allow importing an existing private key into a keystore. To work around
this, we first create a PKCS12 formatted keystore with the certificate and private key and then we
convert it to a Java keystore using keytool.

Prerequisites

 - OpenSSL
 - Java Keytool Utility (available in the JDK, see Keytool)
 - BouncyCastle Provider Library (see BouncyCastle Releases [http://www.bouncycastle.org/latest_releases.html])

Steps

1. Import certificate and private key into PKCS12 keystore.


    openssl pkcs12 -export -out awsiot_keystore.p12 -inkey private.pem.key -in certificate.pem.crt -name awsiot


The alias parameter defines the alias of the cert/key in the keystore. This is used in the SDK to
access the correct certificate and private key entry if the keystore contains more than one.
This command will prompt for a password. This password will be the source password when converting
to BKS in the following step.

2. Convert PKCS12 keystore to a BKS (BouncyCastle) keystore.

Before instal JCE - java cryptography extension for your version of java, look for
Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 8 Download


    keytool -importkeystore -srckeystore awsiot_keystore.p12 -srcstoretype pkcs12 -destkeystore awsiot.bks -deststoretype bks -deststorepass changed -destkeypass changed --provider org.bouncycastle.jce.provider.BouncyCastleProvider -–providerpath bcprov-jdk15on-160.jar
 
This command will prompt for both a destination password and a source password. The source password
is the export password given in the previous step. The destination password will be the password
required to access the private key in the keystore going forward. This password will be required
inside your application when accessing the keystore. You can test the password in the next step.

3. List aliases in keystore to verify (optional).


    keytool -list -v -keystore awsiot.bks -storetype bks -storepass changed -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath bcprov-jdk15on-160.jar

4. Push to Android Emulator (optional).


    adb root
    adb push awsiot.bks /data/user/0/your_app_dir_goes_here/files/awsiot

The directory and filename used will depend on your use case. Typically the application's files
directory is in /data/user/0//files/. You may however choose to locate your keystore on removable
media or another space on the filesystem. The SDK allows for specifying the file path and name of
the keystore so the choice is up to you.

## Enable auto-launch behavior

This sample app is currently configured to launch only when deployed from your
development machine. To enable the main activity to launch automatically on boot,
add the following `intent-filter` to the app's manifest file:

```xml
<activity ...>

    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.HOME"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>

</activity>
```

## License

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
