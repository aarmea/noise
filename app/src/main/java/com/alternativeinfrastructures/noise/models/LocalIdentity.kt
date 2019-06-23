package com.alternativeinfrastructures.noise.models

import com.alternativeinfrastructures.noise.NoiseDatabase
import com.alternativeinfrastructures.noise.models.signal.TypeConverters
import com.alternativeinfrastructures.noise.storage.IdentityAnnouncementMessage
import com.alternativeinfrastructures.noise.storage.UnknownMessage
import com.raizlabs.android.dbflow.annotation.Column
import com.raizlabs.android.dbflow.annotation.ForeignKey
import com.raizlabs.android.dbflow.annotation.Index
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper

import java.io.IOException

import io.reactivex.Single

@Table(database = NoiseDatabase::class)
class LocalIdentity : BaseRXModel() {

    @Index
    var username: String = ""
        protected set

    var registrationId: Int = 0
        protected set

    @PrimaryKey
    @Column(typeConverter = TypeConverters.IdentityKeyPairConverter::class)
    lateinit var identityKeyPair: IdentityKeyPair

    @Column(typeConverter = TypeConverters.SignedPreKeyRecordConverter::class)
    lateinit var signedPreKey: SignedPreKeyRecord

    @ForeignKey
    var remoteIdentity: RemoteIdentity? = null

    private fun createRemoteIdentity(): RemoteIdentity {
        // TODO: Is using the registrationId as the deviceId okay?
        return RemoteIdentity(username, registrationId, identityKeyPair.publicKey)
    }

    companion object {
        fun validUsername(username: String?): Boolean {
            // TODO: Other constraints?
            return username != null && username.length >= 5 && username.length <= 31
        }

        @Throws(IOException::class, InvalidKeyException::class, UnknownMessage.PayloadTooLargeException::class)
        fun createNew(username: String): Single<Boolean> {
            val identity = LocalIdentity()
            identity.username = username
            identity.registrationId = KeyHelper.generateRegistrationId(true /*extendedRange*/)
            identity.identityKeyPair = KeyHelper.generateIdentityKeyPair()
            // TODO: Once there's support for multiple identities, increment the signedPreKeyId
            identity.signedPreKey = KeyHelper.generateSignedPreKey(
                    identity.identityKeyPair, 0 /*signedPreKeyId*/)
            val remoteIdentity = identity.createRemoteIdentity()
            identity.remoteIdentity = remoteIdentity

            return IdentityAnnouncementMessage.createAndSignAsync(remoteIdentity, IdentityAnnouncementMessage.DEFAULT_ZERO_BITS)
                    .flatMap { UnknownMessage -> identity.save() }
        }
    }

    class MissingLocalIdentity : Exception()
}
