with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

import re

# Find the signingConfigs block
pattern = r'  // signingConfigs \{.*?\n  \}'
# wait, it's easier to just do a string replace since I know the exact content

content = content.replace('''  // signingConfigs {
    // create("release") {
      // val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      // storeFile = file(keystorePath)
      // storePassword = System.getenv("STORE_PASSWORD")
      // keyAlias = "upload"
      // keyPassword = System.getenv("KEY_PASSWORD")
    }
    // create("debugConfig") {
      // storeFile = file("${rootDir}/debug.keystore")
      // storePassword = "android"
      // keyAlias = "androiddebugkey"
      // keyPassword = "android"
    }
  }''', '''  // signingConfigs {
  //   create("release") {
  //     val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
  //     storeFile = file(keystorePath)
  //     storePassword = System.getenv("STORE_PASSWORD")
  //     keyAlias = "upload"
  //     keyPassword = System.getenv("KEY_PASSWORD")
  //   }
  //   create("debugConfig") {
  //     storeFile = file("${rootDir}/debug.keystore")
  //     storePassword = "android"
  //     keyAlias = "androiddebugkey"
  //     keyPassword = "android"
  //   }
  // }''')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
