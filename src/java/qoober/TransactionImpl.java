/*
 * Copyright © 2013-2016 The Qoober Core Developers.
 * Copyright © 2016-2020 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Qoober software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package qoober;

import qoober.QooberException.NotValidException;
import qoober.crypto.Crypto;
import qoober.db.DbKey;
import qoober.util.Convert;
import qoober.util.Filter;
import qoober.util.Logger;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class TransactionImpl implements Transaction {

    static final class BuilderImpl implements Builder {

        private final short deadline;
        private final byte[] senderPublicKey;
        private final long amountNQT;
        private final long feeNQT;
        private final TransactionType type;
        private final byte version;
        private Attachment.AbstractAttachment attachment;

        private long recipientId;
        private byte[] referencedTransactionFullHash;
        private byte[] signature;
        private Appendix.Message message;
        private Appendix.EncryptedMessage encryptedMessage;
        private Appendix.EncryptToSelfMessage encryptToSelfMessage;
        private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
        private Appendix.Phasing phasing;
        private Appendix.PrunablePlainMessage prunablePlainMessage;
        private Appendix.PrunableEncryptedMessage prunableEncryptedMessage;
        private long blockId;
        private int height = Integer.MAX_VALUE;
        private long id;
        private long senderId;
        private int timestamp = Integer.MAX_VALUE;
        private int blockTimestamp = -1;
        private byte[] fullHash;
        private boolean ecBlockSet = false;
        private int ecBlockHeight;
        private long ecBlockId;
        private short index = -1;
        private boolean isGenesisBlock;

        BuilderImpl(byte version, byte[] senderPublicKey, long amountNQT, long feeNQT, short deadline,
                    Attachment.AbstractAttachment attachment) {
            this.version = version;
            this.deadline = deadline;
            this.senderPublicKey = senderPublicKey;
            this.amountNQT = amountNQT;
            this.feeNQT = feeNQT;
            this.attachment = attachment;
            this.type = attachment.getTransactionType();
        }

        @Override
        public TransactionImpl build(String secretPhrase) throws NotValidException {
            if (timestamp == Integer.MAX_VALUE) {
                timestamp = Qoober.getEpochTime();
            }
            if (!ecBlockSet) {
                Block ecBlock = BlockchainImpl.getInstance().getECBlock(timestamp);
                this.ecBlockHeight = ecBlock.getHeight();
                this.ecBlockId = ecBlock.getId();
            }
            return new TransactionImpl(this, secretPhrase);
        }

        @Override
        public TransactionImpl build() throws NotValidException {
            return build(null);
        }

        public BuilderImpl recipientId(long recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        @Override
        public BuilderImpl referencedTransactionFullHash(String referencedTransactionFullHash) {
            this.referencedTransactionFullHash = Convert.parseHexString(referencedTransactionFullHash);
            return this;
        }

        BuilderImpl referencedTransactionFullHash(byte[] referencedTransactionFullHash) {
            this.referencedTransactionFullHash = referencedTransactionFullHash;
            return this;
        }

        BuilderImpl appendix(Attachment.AbstractAttachment attachment) {
            this.attachment = attachment;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.Message message) {
            this.message = message;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptedMessage encryptedMessage) {
            this.encryptedMessage = encryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptToSelfMessage encryptToSelfMessage) {
            this.encryptToSelfMessage = encryptToSelfMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PublicKeyAnnouncement publicKeyAnnouncement) {
            this.publicKeyAnnouncement = publicKeyAnnouncement;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunablePlainMessage prunablePlainMessage) {
            this.prunablePlainMessage = prunablePlainMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunableEncryptedMessage prunableEncryptedMessage) {
            this.prunableEncryptedMessage = prunableEncryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.Phasing phasing) {
            this.phasing = phasing;
            return this;
        }

        @Override
        public BuilderImpl timestamp(int timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public BuilderImpl ecBlockHeight(int height) {
            this.ecBlockHeight = height;
            this.ecBlockSet = true;
            return this;
        }

        @Override
        public BuilderImpl ecBlockId(long blockId) {
            this.ecBlockId = blockId;
            this.ecBlockSet = true;
            return this;
        }

        BuilderImpl id(long id) {
            this.id = id;
            return this;
        }

        BuilderImpl signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        BuilderImpl blockId(long blockId) {
            this.blockId = blockId;
            return this;
        }

        BuilderImpl height(int height) {
            this.height = height;
            return this;
        }

        BuilderImpl senderId(long senderId) {
            this.senderId = senderId;
            return this;
        }

        BuilderImpl fullHash(byte[] fullHash) {
            this.fullHash = fullHash;
            return this;
        }

        BuilderImpl blockTimestamp(int blockTimestamp) {
            this.blockTimestamp = blockTimestamp;
            return this;
        }

        BuilderImpl index(short index) {
            this.index = index;
            return this;
        }

        public BuilderImpl isGenesisBlock(boolean genesisBlock) {
            isGenesisBlock = genesisBlock;
            return this;
        }
    }

    private final short deadline;
    private volatile byte[] senderPublicKey;
    private final long recipientId;
    private final long amountNQT;
    private final long feeNQT;
    private final byte[] referencedTransactionFullHash;
    private final TransactionType type;
    private final int ecBlockHeight;
    private final long ecBlockId;
    private final byte version;
    private final int timestamp;
    private final byte[] signature;
    private final Attachment.AbstractAttachment attachment;
    private final Appendix.Message message;
    private final Appendix.EncryptedMessage encryptedMessage;
    private final Appendix.EncryptToSelfMessage encryptToSelfMessage;
    private final Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
    private final Appendix.Phasing phasing;
    private final Appendix.PrunablePlainMessage prunablePlainMessage;
    private final Appendix.PrunableEncryptedMessage prunableEncryptedMessage;

    private final List<Appendix.AbstractAppendix> appendages;
    private final int appendagesSize;

    private volatile int height;
    private volatile long blockId;
    private volatile BlockImpl block;
    private volatile int blockTimestamp;
    private volatile short index;
    private volatile long id;
    private volatile String stringId;
    private volatile long senderId;
    private volatile byte[] fullHash;
    private volatile DbKey dbKey;
    private volatile byte[] bytes = null;


    private TransactionImpl(BuilderImpl builder, String secretPhrase) throws NotValidException {

        this.timestamp = builder.timestamp;
        this.deadline = builder.deadline;
        this.senderPublicKey = builder.senderPublicKey;
        this.recipientId = builder.recipientId;
        this.amountNQT = builder.amountNQT;
        this.referencedTransactionFullHash = builder.referencedTransactionFullHash;
        this.type = builder.type;
        this.version = builder.version;
        this.blockId = builder.blockId;
        this.height = builder.height;
        this.index = builder.index;
        this.id = builder.id;
        this.senderId = builder.senderId;
        this.blockTimestamp = builder.blockTimestamp;
        this.fullHash = builder.fullHash;
		this.ecBlockHeight = builder.ecBlockHeight;
        this.ecBlockId = builder.ecBlockId;

        List<Appendix.AbstractAppendix> list = new ArrayList<>();
        if ((this.attachment = builder.attachment) != null) {
            list.add(this.attachment);
        }
        if ((this.message  = builder.message) != null) {
            list.add(this.message);
        }
        if ((this.encryptedMessage = builder.encryptedMessage) != null) {
            list.add(this.encryptedMessage);
        }
        if ((this.publicKeyAnnouncement = builder.publicKeyAnnouncement) != null) {
            list.add(this.publicKeyAnnouncement);
        }
        if ((this.encryptToSelfMessage = builder.encryptToSelfMessage) != null) {
            list.add(this.encryptToSelfMessage);
        }
        if ((this.phasing = builder.phasing) != null) {
            list.add(this.phasing);
        }
        if ((this.prunablePlainMessage = builder.prunablePlainMessage) != null) {
            list.add(this.prunablePlainMessage);
        }
        if ((this.prunableEncryptedMessage = builder.prunableEncryptedMessage) != null) {
            list.add(this.prunableEncryptedMessage);
        }
        this.appendages = Collections.unmodifiableList(list);
        int appendagesSize = 0;
        for (Appendix appendage : appendages) {
            if (secretPhrase != null && appendage instanceof Appendix.Encryptable) {
                ((Appendix.Encryptable)appendage).encrypt(secretPhrase);
            }
            appendagesSize += appendage.getSize();
        }
        this.appendagesSize = appendagesSize;
        if (builder.isGenesisBlock) {
            feeNQT = builder.feeNQT;
        } else if (builder.feeNQT <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (height < Integer.MAX_VALUE ? height : Qoober.getBlockchain().getHeight());
            long minFee = getMinimumFeeNQT(effectiveHeight);
            feeNQT = Math.max(minFee, builder.feeNQT);
        } else {
            feeNQT = builder.feeNQT;
        }

        if (builder.signature != null && secretPhrase != null) {
            throw new NotValidException("Transaction is already signed");
        } else if (builder.signature != null) {
            this.signature = builder.signature;
        } else if (secretPhrase != null) {
            if (getSenderPublicKey() != null && ! Arrays.equals(senderPublicKey, Crypto.getPublicKey(secretPhrase))) {
                throw new NotValidException("Secret phrase doesn't match transaction sender public key");
            }
            signature = Crypto.sign(bytes(), secretPhrase);
            bytes = null;
        } else {
            signature = null;
        }

    }

    @Override
    public short getDeadline() {
        return deadline;
    }

    @Override
    public byte[] getSenderPublicKey() {
        if (senderPublicKey == null) {
            senderPublicKey = Account.getPublicKey(senderId);
        }
        return senderPublicKey;
    }

    @Override
    public long getRecipientId() {
        return recipientId;
    }

    @Override
    public long getAmountNQT() {
        return amountNQT;
    }

    @Override
    public long getFeeNQT() {
        return feeNQT;
    }

    long[] getBackFees() {
        return type.getBackFees(this);
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return Convert.toHexString(referencedTransactionFullHash);
    }

    byte[] referencedTransactionFullHash() {
        return referencedTransactionFullHash;
    }

    @Override
    public int getHeight() {
        return height;
    }

    void setHeight(int height) {
        this.height = height;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public TransactionType getType() {
        return type;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    @Override
    public long getBlockId() {
        return blockId;
    }

    @Override
    public BlockImpl getBlock() {
        if (block == null && blockId != 0) {
            block = BlockchainImpl.getInstance().getBlock(blockId);
        }
        return block;
    }

    void setBlock(BlockImpl block) {
        this.block = block;
        this.blockId = block.getId();
        this.height = block.getHeight();
        this.blockTimestamp = block.getTimestamp();
    }

    void unsetBlock() {
        this.block = null;
        this.blockId = 0;
        this.blockTimestamp = -1;
        this.index = -1;
        // must keep the height set, as transactions already having been included in a popped-off block before
        // get priority when sorted for inclusion in a new block
    }

    @Override
    public short getIndex() {
        if (index == -1) {
            throw new IllegalStateException("Transaction index has not been set");
        }
        return index;
    }

    void setIndex(int index) {
        this.index = (short)index;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public int getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public int getExpiration() {
        return timestamp + deadline * 60;
    }

    @Override
    public Attachment.AbstractAttachment getAttachment() {
        attachment.loadPrunable(this);
        return attachment;
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages() {
        return getAppendages(false);
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages(boolean includeExpiredPrunable) {
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this, includeExpiredPrunable);
        }
        return appendages;
    }

    @Override
    public List<Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        List<Appendix> result = new ArrayList<>();
        appendages.forEach(appendix -> {
            if (filter.ok(appendix)) {
                appendix.loadPrunable(this, includeExpiredPrunable);
                result.add(appendix);
            }
        });
        return result;
    }

    @Override
    public long getId() {
        if (id == 0) {
            if (signature == null) {
                throw new IllegalStateException("Transaction is not signed yet");
            }
            byte[] data = zeroSignature(getBytes());
            byte[] signatureHash = Crypto.sha256().digest(signature);
            MessageDigest digest = Crypto.sha256();
            digest.update(data);
            fullHash = digest.digest(signatureHash);
            BigInteger bigInteger = new BigInteger(1, new byte[] {fullHash[7], fullHash[6], fullHash[5], fullHash[4], fullHash[3], fullHash[2], fullHash[1], fullHash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public String getFullHash() {
        return Convert.toHexString(fullHash());
    }

    byte[] fullHash() {
        if (fullHash == null) {
            getId();
        }
        return fullHash;
    }

    @Override
    public long getSenderId() {
        if (senderId == 0) {
            senderId = Account.getId(getSenderPublicKey());
        }
        return senderId;
    }

    DbKey getDbKey() {
        if (dbKey == null) {
            dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDbKeyFactory.newKey(getId());
        }
        return dbKey;
    }

    @Override
    public Appendix.Message getMessage() {
        return message;
    }

    @Override
    public Appendix.EncryptedMessage getEncryptedMessage() {
        return encryptedMessage;
    }

    @Override
    public Appendix.EncryptToSelfMessage getEncryptToSelfMessage() {
        return encryptToSelfMessage;
    }

    @Override
    public Appendix.Phasing getPhasing() {
        return phasing;
    }

    boolean attachmentIsPhased() {
        return attachment.isPhased(this);
    }

    Appendix.PublicKeyAnnouncement getPublicKeyAnnouncement() {
        return publicKeyAnnouncement;
    }

    @Override
    public Appendix.PrunablePlainMessage getPrunablePlainMessage() {
        if (prunablePlainMessage != null) {
            prunablePlainMessage.loadPrunable(this);
        }
        return prunablePlainMessage;
    }

    boolean hasPrunablePlainMessage() {
        return prunablePlainMessage != null;
    }

    @Override
    public Appendix.PrunableEncryptedMessage getPrunableEncryptedMessage() {
        if (prunableEncryptedMessage != null) {
            prunableEncryptedMessage.loadPrunable(this);
        }
        return prunableEncryptedMessage;
    }

    boolean hasPrunableEncryptedMessage() {
        return prunableEncryptedMessage != null;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(type.getType());
                buffer.put((byte) ((version << 4) | type.getSubtype()));
                buffer.putInt(timestamp);
                buffer.putShort(deadline);
                buffer.put(getSenderPublicKey());
                buffer.putLong(type.canHaveRecipient() ? recipientId : Genesis.CREATOR_ID);
                buffer.putLong(amountNQT);
                buffer.putLong(feeNQT);
                if (referencedTransactionFullHash != null) {
                    buffer.put(referencedTransactionFullHash);
                } else {
                    buffer.put(new byte[32]);
                }
                buffer.put(signature != null ? signature : new byte[64]);
                buffer.putInt(getFlags());
                buffer.putInt(ecBlockHeight);
                buffer.putLong(ecBlockId);
                for (Appendix appendage : appendages) {
                    appendage.putBytes(buffer);
                }
                bytes = buffer.array();
            } catch (RuntimeException e) {
                if (signature != null) {
                    Logger.logDebugMessage("Failed to get transaction bytes for transaction: " + getJSONObject().toJSONString());
                }
                throw e;
            }
        }
        return bytes;
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes) throws NotValidException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            byte type = buffer.get();
            byte subtype = buffer.get();
            byte version = (byte) ((subtype & 0xF0) >> 4);
            subtype = (byte) (subtype & 0x0F);
            int timestamp = buffer.getInt();
            short deadline = buffer.getShort();
            byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            long recipientId = buffer.getLong();
            long amountNQT = buffer.getLong();
            long feeNQT = buffer.getLong();
            byte[] referencedTransactionFullHash = new byte[32];
            buffer.get(referencedTransactionFullHash);
            referencedTransactionFullHash = Convert.emptyToNull(referencedTransactionFullHash);
            byte[] signature = new byte[64];
            buffer.get(signature);
            signature = Convert.emptyToNull(signature);
            int flags = 0;
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                flags = buffer.getInt();
                ecBlockHeight = buffer.getInt();
                ecBlockId = buffer.getLong();
            }
            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey, amountNQT, feeNQT,
                    deadline, transactionType.parseAttachment(buffer))
                    .timestamp(timestamp)
                    .referencedTransactionFullHash(referencedTransactionFullHash)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            int position = 1;
            if ((flags & position) != 0 || (version == 0 && transactionType == TransactionType.Messaging.ARBITRARY_MESSAGE)) {
                builder.appendix(new Appendix.Message(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptedMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PublicKeyAnnouncement(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptToSelfMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.Phasing(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunablePlainMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunableEncryptedMessage(buffer));
            }
            if (buffer.hasRemaining()) {
                throw new NotValidException("Transaction bytes too long, " + buffer.remaining() + " extra bytes");
            }
            return builder;
        } catch (NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(bytes));
            throw e;
        }
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes, JSONObject prunableAttachments) throws NotValidException {
        BuilderImpl builder = newTransactionBuilder(bytes);
        if (prunableAttachments != null) {
            Attachment.ShufflingProcessing shufflingProcessing = Attachment.ShufflingProcessing.parse(prunableAttachments);
            if (shufflingProcessing != null) {
                builder.appendix(shufflingProcessing);
            }
            Attachment.TaggedDataUpload taggedDataUpload = Attachment.TaggedDataUpload.parse(prunableAttachments);
            if (taggedDataUpload != null) {
                builder.appendix(taggedDataUpload);
            }
            Attachment.TaggedDataExtend taggedDataExtend = Attachment.TaggedDataExtend.parse(prunableAttachments);
            if (taggedDataExtend != null) {
                builder.appendix(taggedDataExtend);
            }
            Appendix.PrunablePlainMessage prunablePlainMessage = Appendix.PrunablePlainMessage.parse(prunableAttachments);
            if (prunablePlainMessage != null) {
                builder.appendix(prunablePlainMessage);
            }
            Appendix.PrunableEncryptedMessage prunableEncryptedMessage = Appendix.PrunableEncryptedMessage.parse(prunableAttachments);
            if (prunableEncryptedMessage != null) {
                builder.appendix(prunableEncryptedMessage);
            }
        }
        return builder;
    }

    public byte[] getUnsignedBytes() {
        return zeroSignature(getBytes());
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("type", type.getType());
        json.put("subtype", type.getSubtype());
        json.put("timestamp", timestamp);
        json.put("deadline", deadline);
        json.put("senderPublicKey", Convert.toHexString(getSenderPublicKey()));
        if (type.canHaveRecipient()) {
            json.put("recipient", Long.toUnsignedString(recipientId));
        }
        json.put("amountNQT", amountNQT);
        json.put("feeNQT", feeNQT);
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", Convert.toHexString(referencedTransactionFullHash));
        }
        json.put("ecBlockHeight", ecBlockHeight);
        json.put("ecBlockId", Long.toUnsignedString(ecBlockId));
        json.put("signature", Convert.toHexString(signature));
        JSONObject attachmentJSON = new JSONObject();
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            attachmentJSON.putAll(appendage.getJSONObject());
        }
        if (! attachmentJSON.isEmpty()) {
            json.put("attachment", attachmentJSON);
        }
        json.put("version", version);
        return json;
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        JSONObject prunableJSON = null;
        for (Appendix.AbstractAppendix appendage : appendages) {
            if (appendage instanceof Appendix.Prunable) {
                appendage.loadPrunable(this);
                if (prunableJSON == null) {
                    prunableJSON = appendage.getJSONObject();
                } else {
                    prunableJSON.putAll(appendage.getJSONObject());
                }
            }
        }
        return prunableJSON;
    }

    static TransactionImpl parseTransaction(JSONObject transactionData) throws NotValidException {
        TransactionImpl transaction = newTransactionBuilder(transactionData).build();
        if (transaction.getSignature() != null && !transaction.checkSignature()) {
            throw new NotValidException("Invalid transaction signature for transaction " + transaction.getJSONObject().toJSONString());
        }
        return transaction;
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(JSONObject transactionData) throws NotValidException {
        try {
            byte type = ((Long) transactionData.get("type")).byteValue();
            byte subtype = ((Long) transactionData.get("subtype")).byteValue();
            int timestamp = ((Long) transactionData.get("timestamp")).intValue();
            short deadline = ((Long) transactionData.get("deadline")).shortValue();
            byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
            long amountNQT = Convert.parseLong(transactionData.get("amountNQT"));
            long feeNQT = Convert.parseLong(transactionData.get("feeNQT"));
            String referencedTransactionFullHash = (String) transactionData.get("referencedTransactionFullHash");
            byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));
            Long versionValue = (Long) transactionData.get("version");
            byte version = versionValue == null ? 0 : versionValue.byteValue();
            JSONObject attachmentData = (JSONObject) transactionData.get("attachment");
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                ecBlockHeight = ((Long) transactionData.get("ecBlockHeight")).intValue();
                ecBlockId = Convert.parseUnsignedLong((String) transactionData.get("ecBlockId"));
            }

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            if (transactionType == null) {
                throw new NotValidException("Invalid transaction type: " + type + ", " + subtype);
            }
            TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey,
                    amountNQT, feeNQT, deadline,
                    transactionType.parseAttachment(attachmentData))
                    .timestamp(timestamp)
                    .referencedTransactionFullHash(referencedTransactionFullHash)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
                builder.recipientId(recipientId);
            }
            if (attachmentData != null) {
                builder.appendix(Appendix.Message.parse(attachmentData));
                builder.appendix(Appendix.EncryptedMessage.parse(attachmentData));
                builder.appendix((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
                builder.appendix(Appendix.EncryptToSelfMessage.parse(attachmentData));
                builder.appendix(Appendix.Phasing.parse(attachmentData));
                builder.appendix(Appendix.PrunablePlainMessage.parse(attachmentData));
                builder.appendix(Appendix.PrunableEncryptedMessage.parse(attachmentData));
            }
            return builder;
        } catch (NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
            throw e;
        }
    }


    @Override
    public int getECBlockHeight() {
        return ecBlockHeight;
    }

    @Override
    public long getECBlockId() {
        return ecBlockId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TransactionImpl && this.getId() == ((Transaction)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    public boolean verifySignature() {
        return checkSignature() && Account.setOrVerify(getSenderId(), getSenderPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() {
        if (!hasValidSignature) {
            hasValidSignature = signature != null && Crypto.verify(signature, zeroSignature(getBytes()), getSenderPublicKey());
        }
        return hasValidSignature;
    }

    private int getSize() {
        return signatureOffset() + 64  + 4 + 4 + 8 + appendagesSize;
    }

    @Override
    public int getFullSize() {
        int fullSize = getSize() - appendagesSize;
        for (Appendix.AbstractAppendix appendage : getAppendages()) {
            fullSize += appendage.getFullSize();
        }
        return fullSize;
    }

    private int signatureOffset() {
        return 1 + 1 + 4 + 2 + 32 + 8 + 8 + 8 + 32;
    }

    private byte[] zeroSignature(byte[] data) {
        int start = signatureOffset();
        for (int i = start; i < start + 64; i++) {
            data[i] = 0;
        }
        return data;
    }

    private int getFlags() {
        int flags = 0;
        int position = 1;
        if (message != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptedMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (publicKeyAnnouncement != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptToSelfMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (phasing != null) {
            flags |= position;
        }
        position <<= 1;
        if (prunablePlainMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (prunableEncryptedMessage != null) {
            flags |= position;
        }
        return flags;
    }

    private static void assertFalse(boolean expression, Supplier<String> message) throws NotValidException {
        if (expression) {
            throw new NotValidException(message.get());
        }
    }

    @Override
    public void validate() throws QooberException.ValidationException {
        if (timestamp == 0) {
            assertFalse(deadline != 0 , () -> String.format("Invalid transaction parameters:\n timestamp == 0 && deadline(%s) != 0", deadline));
            assertFalse(feeNQT != 0 , () -> String.format("Invalid transaction parameters:\n timestamp == 0 && feeNQT(%s) != 0", feeNQT));
        } else {
            assertFalse(deadline < 1, () -> String.format("Invalid transaction parameters:\n timestamp(%s) != 0 && deadline(%s) < 1", timestamp, deadline));
            assertFalse(feeNQT <= 0 , () -> String.format("Invalid transaction parameters:\n timestamp(%s) != 0 && feeNQT(%s) <= 0", timestamp, feeNQT));
        }

        assertFalse(feeNQT > Constants.MAX_BALANCE_QNT, () -> String.format("Invalid transaction parameters:\n feeNQT(%s) > Constants.MAX_BALANCE_QNT(%s)", feeNQT, Constants.MAX_BALANCE_QNT));
        assertFalse(amountNQT < 0, () -> String.format("Invalid transaction parameters:\n amountNQT(%s) < 0", amountNQT));
        assertFalse(amountNQT > Constants.MAX_BALANCE_QNT, () -> String.format("Invalid transaction parameters:\n amountNQT(%s) > Constants.MAX_BALANCE_QNT(%s)", amountNQT, Constants.MAX_BALANCE_QNT));
        assertFalse(type == null, () -> "Invalid transaction parameters:\n type: null");

        if (referencedTransactionFullHash != null && referencedTransactionFullHash.length != 32) {
            throw new NotValidException("Invalid referenced transaction full hash " + Convert.toHexString(referencedTransactionFullHash));
        }

        if (attachment == null || type != attachment.getTransactionType()) {
            throw new NotValidException("Invalid attachment " + attachment + " for transaction of type " + type);
        }

        if (! type.canHaveRecipient()) {
            if (recipientId != 0 || getAmountNQT() != 0) {
                throw new NotValidException("Transactions of this type must have recipient == 0, amount == 0");
            }
        }

        if (type.mustHaveRecipient()) {
            if (recipientId == 0) {
                throw new NotValidException("Transactions of this type must have a valid recipient");
            }
        }

        boolean validatingAtFinish = phasing != null && getSignature() != null && PhasingPoll.getPoll(getId()) != null;
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            if (! appendage.verifyVersion()) {
                throw new NotValidException("Invalid attachment version " + appendage.getVersion());
            }
            if (validatingAtFinish) {
                appendage.validateAtFinish(this);
            } else {
                appendage.validate(this);
            }
        }

        if (getFullSize() > Constants.MAX_PAYLOAD_LENGTH) {
            throw new NotValidException("Transaction size " + getFullSize() + " exceeds maximum payload size");
        }
        int blockchainHeight = Qoober.getBlockchain().getHeight();
        if (!validatingAtFinish) {
            long minimumFeeNQT = getMinimumFeeNQT(blockchainHeight);
            if (feeNQT < minimumFeeNQT) {
                throw new QooberException.NotCurrentlyValidException(String.format("Transaction fee %f %s less than minimum fee %f %s at height %d",
                        ((double) feeNQT) / Constants.ONE_QBR, Constants.COIN_SYMBOL, ((double) minimumFeeNQT) / Constants.ONE_QBR, Constants.COIN_SYMBOL, blockchainHeight));
            }
            if (ecBlockId != 0) {
                if (blockchainHeight < ecBlockHeight) {
                    throw new QooberException.NotCurrentlyValidException("ecBlockHeight " + ecBlockHeight
                            + " exceeds blockchain height " + blockchainHeight);
                }
                if (BlockDb.findBlockIdAtHeight(ecBlockHeight) != ecBlockId) {
                    throw new QooberException.NotCurrentlyValidException("ecBlockHeight " + ecBlockHeight
                            + " does not match ecBlockId " + Long.toUnsignedString(ecBlockId)
                            + ", transaction was generated on a fork");
                }
            }
            AccountRestrictions.checkTransaction(this);
        }
    }

    // returns false iff double spending
    boolean applyUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        return senderAccount != null && type.applyUnconfirmed(this, senderAccount);
    }

    void apply() {
        Account senderAccount = Account.getAccount(getSenderId());
        senderAccount.apply(getSenderPublicKey());
        Account recipientAccount = null;
        if (recipientId != 0) {
            recipientAccount = Account.getAccount(recipientId);
            if (recipientAccount == null) {
                recipientAccount = Account.addOrGetAccount(recipientId);
            }
        }
        if (referencedTransactionFullHash != null) {
            senderAccount.addToUnconfirmedBalanceNQT(getType().getLedgerEvent(), getId(),
                    0, Constants.UNCONFIRMED_POOL_DEPOSIT_QNT);
        }
        if (attachmentIsPhased()) {
            senderAccount.addToBalanceNQT(getType().getLedgerEvent(), getId(), 0, -feeNQT);
        }
        for (Appendix.AbstractAppendix appendage : appendages) {
            if (!appendage.isPhased(this)) {
                appendage.loadPrunable(this);
                appendage.apply(this, senderAccount, recipientAccount);
            }
        }
    }

    void undoUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        type.undoUnconfirmed(this, senderAccount);
    }

    boolean attachmentIsDuplicate(Map<TransactionType, Map<String, Integer>> duplicates, boolean atAcceptanceHeight) {
        if (!attachmentIsPhased() && !atAcceptanceHeight) {
            // can happen for phased transactions having non-phasable attachment
            return false;
        }
        if (atAcceptanceHeight) {
            if (AccountRestrictions.isBlockDuplicate(this, duplicates)) {
                return true;
            }
            // all are checked at acceptance height for block duplicates
            if (type.isBlockDuplicate(this, duplicates)) {
                return true;
            }
            // phased are not further checked at acceptance height
            if (attachmentIsPhased()) {
                return false;
            }
        }
        // non-phased at acceptance height, and phased at execution height
        return type.isDuplicate(this, duplicates);
    }

    boolean isUnconfirmedDuplicate(Map<TransactionType, Map<String, Integer>> duplicates) {
        return type.isUnconfirmedDuplicate(this, duplicates);
    }

    private long getMinimumFeeNQT(int blockchainHeight) {
        long totalFee = 0;
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            if (blockchainHeight < appendage.getBaselineFeeHeight()) {
                return 0; // Not possible to validate min fees before the baseline block of any one appendage
            }
            Fee fee = blockchainHeight >= appendage.getNextFeeHeight() ? appendage.getNextFee(this) : appendage.getBaselineFee(this);
            totalFee = Math.addExact(totalFee, fee.getFee(this, appendage));
        }
        if (referencedTransactionFullHash != null) {
            totalFee = Math.addExact(totalFee, Constants.ONE_QBR);
        }
        return totalFee;
    }

}