## Deep Telegram messenger for Android

This repo contains the official source code for [Deep Telegram App for Android](https://play.google.com/store/apps/details?id=it.deeptelegram.messenger).

Hello!
I have created another fork of the telegram app that allows you to access the deep telegram!
The deep telegram is like a second unexplored telegram hosted on secondary testing servers.  

On deep telegram you can create up to two channels/groups with public usernames.

On deep telegram the limit for supergroups is of 20 users, while we still haven't reached the limit for normal groups.  

If you try to upgrade a normal group with more than 20 users to a supergroup telegram will randomly kick users to stay within the 20 users limit of supergroups. Note that bots won't be kicked.

On deep telegram the equivalent of @botfather is @father_bot

On deep telegram the following official bots are present: @sticker, @spambot, @like, @vote.
@gif and @vid are also present but they don't work.
In deep pwrtelegram, @vote and @like work in channels (ergo the callback data)!


Obviously the official bot API cannot be used to interface with bots since it connects to the main telegram servers, but if you use the @pwrtelegram app (after switching backend of course) you can login as a bot.
I managed to install the pwrtelegram API in deep telegram. 
To run bots in deep telegram you just have to create a bot @father_bot and then access it using the Deep PWRTelegram bot API.
Like the original bot API, it can upload/download files up to 1.5 gb, features anonymous downloads and much more!
See https://pwrtelegram.xyz for the full documentation of the API (it's pretty much the same as the official bot API).
The main API endpoint is https://deepapi.pwrtelegram.xyz, 
The main storage endpoint is https://deepstorage.pwrtelegram.xyz.

See pwrtelegram.xyz for more details.

Secret chats work properly on deep telegram.

If you're spamreported on telegram, you will be spamreported on deep telegram.
If you're spamreported on deep telegram, you won't be spamreported on telegram.
Looks like telegram spamreports are extended to deep telegram only if they are permanent.

On deep telegram channels can have max 3 admins.

To access deep telegram on iOS, windows phone and other systems use telegram web with the ?test=1 query string.
https://web.telegram.org?test=1

I've finished writing https://telegram.me/deepbridgebot, now you can use it to transfer files and messages from telegram to deep telegram and vice versa.


Daniil Gentili