# Noise

A chat app for the end of the world.

[![Build Status](https://travis-ci.org/aarmea/noise.svg?branch=master)](https://travis-ci.org/aarmea/noise)

## What's wrong with chat today

On traditional chat platforms, mobile devices connect over the Internet to a central server which is responsible for relaying messages to their destinations. This has a few problems:

* Cell phone towers and the servers implementing chat services are single points of failure.
* Natural disasters can easily wipe out infrastructure. In Puerto Rico, [it took months to restore cell phone service after Hurricane Maria][cellservice-maria].
* Cell phone service tends to be unreliable in sparsely populated areas.
* At concerts, conventions, protests, or similar situations, cell phone towers can be overloaded making it difficult to get packets through.
* In [China][censorship-china], [North Korea][censorship-nk], [Turkey][censorship-turkey], and anywhere else that freedom of speech is not respected, existing communications infrastructure is monitored and censored.

[cellservice-maria]: https://en.wikipedia.org/wiki/Hurricane_Maria#Puerto_Rico_3
[censorship-china]: https://en.wikipedia.org/wiki/Great_Firewall
[censorship-nk]: https://en.wikipedia.org/wiki/Human_rights_in_North_Korea#Civil_liberties
[censorship-turkey]: https://www.afp.com/en/news/826/turkey-gives-watchdog-power-block-internet-broadcasts-doc-12z0r61

## How Noise will fix this

A formal paper describing Noise could be titled: "Adapting epidemic routing for commodity phones in adversarial conditions". Let's unwrap this:

* [Epidemic routing][epidemic-routing] is a store-and-forward protocol that allows messages to travel long distances over random connections between any two nodes. These connections can happen if two Noise users enter each other's vicinity. When this happens, the app will automatically connect and sync messages as long as they are close enough.
* To enable this as a plug-and-play solution, [I implemented a battery-efficient way for Android apps to automatically discover and connect to nearby devices over Bluetooth][bt-auto-connect]. Once you install Noise, your phone is part of the network and can send and receive messages. No extra hardware is needed.
* To prevent spam and jamming, individual messages require a [proof-of-work][proof-of-work]. This makes it computationally difficult to create new messages but easy to verify them while syncing. Messages that fail this check are discarded.
* The proof-of-work is also used to assign importance to an individual message. While syncing, more recent and more important messages are sent first, and less important messages are deleted first when the database exceeds its preset size limit. In this way, the proof-of-work also establishes a pseudo-[TTL][ttl].

[epidemic-routing]: http://issg.cs.duke.edu/epidemic/epidemic.pdf
[bt-auto-connect]: https://albertarmea.com/post/bt-auto-connect/
[proof-of-work]: http://www.hashcash.org/papers/bread-pudding.pdf
[ttl]: https://en.wikipedia.org/wiki/Time_to_live

This provides a prioritized flat database of cleartext messages. Two-way chat can be implemented on top of this:

* Identity announcement can just be a message with high importance. By including a public key here, others will be able to send messages to this identity.
* The [Signal protocol][signal-protocol] was originally designed to provide end-to-end encrypted chat on top of SMS. As a result, it works on top of asynchronous links.
* When syncing, Noise can check if any of its private keys can decrypt the message. If it can, then the message has reached its destination.

[signal-protocol]: https://signal.org/docs/

I also plan on making this database available as a local API to allow developers to build robust communications into their apps.

## What works so far

* Epidemic routing over Bluetooth
* Proof-of-work for message validation

## Legal

### Cryptography notice

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted.
See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.
The form and manner of this distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

### Experimental software

This project has not been reviewed by a security expert. If you entrust Noise with sensitive information, you are doing so at your own risk.

### License

You may use Noise under the terms of the MIT license. See LICENSE for more details.