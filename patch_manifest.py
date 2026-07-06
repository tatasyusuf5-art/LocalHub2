import re

with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

content = content.replace(
'''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyApplication">
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout"
            android:screenOrientation="fullSensor"''',
'''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout"
            android:screenOrientation="fullSensor"
            android:theme="@style/Theme.MyApplication">'''
)

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)
