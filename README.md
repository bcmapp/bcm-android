# BCM Android 
## BCM is a highly secure communication platform. Each message is strictly encrypted, and no third party can decipher the content. Below are the highlight features:
### Privacy Protection
1. Sign up to BCM without phone-number or email.
2. No access to your phone contacts.
3. User profiles (avatar/nickname, etc.) are encrypted, and the encryption key is unknown to BCM Servers.
4. For group chat, the group name, group notice are encrypted, and the encryption key is unknown to BCM Servers.
5. The sender Meta data (e.g., UID) is encrypted and unknown to server database.
6. One may choose to hide his/her IP address from the peer during VOIP call.

### Communication Security
1. End-to-end encrypts all your communication, including messages, voice calls, group chats, files, etc. Only the intended recipient, and nobody else can decipher the content. 
2. Burn after reading, for one-to-one communication.
3. Immune to server compromise: Even when BCM Server is fully controlled by malicious people, the encrypted content cannot be deciphered successfully by any non-intended recipients.
4. Man-in-the-middle attack proof: be able to automatically detect fake public key delivered from the server side.
5. One may wipe out the chat history manually or automatically, for both sides, or one side only.
6. One may be reminded to destroy the account soon after illegal login was detected elsewhere.

### High Availability
1. AirChat：Delivers your messages via device-to-device ad hoc network without the Internet, with end-to-end encryption.
2. Proxy: automatically relay BCM traffic through obfuscation proxies when direct access does not work. User may also configure his/her own obfuscation proxy in BCM.

### Other Features
1. Large size group supported, up to 100000 users in one single group.
2. Secure Data Vault: User may use BCM to store some sensitive information locally on the phone.

Currently available on the Play store.

<a href='https://play.google.com/store/apps/details?id=com.bcm.messenger'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height='80px'/></a>


Contributing Bug reports
------------------------
We use GitHub for bug tracking. Please search the existing issues for your bug and create a new one if the issue is not yet tracked!

https://github.com/bcmapp/bcm-android/issues

Joining the Beta
----------------
Want to live life on the bleeding edge and help out with testing?

You can subscribe to BCM Android Beta releases here:
https://play.google.com/apps/testing/com.bcm.messenger
 
If you're interested in a life of peace and tranquility, stick with the standard releases.

## Contributing Code
Instructions on how to setup your development environment and build BCM can be found in  [BUILDING.md](https://github.com/bcmapp/bcm-android/blob/master/BUILDING.md).

If you're new to the BCM codebase, we recommend going through our issues and picking out a simple bug to fix (check the "easy" label in our issues) in order to get yourself familiar. 


Acknowledgments
---------------
Special thanks to Signal, some of the BCM code is based on Signal project.

Legal things
------------
## Cryptography Notice

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted.

## License

Copyright 2019 BCM

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

Google Play and the Google Play logo are trademarks of Google Inc.