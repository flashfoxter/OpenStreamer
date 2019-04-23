/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.media.MediaCodec;

import com.lk.openstreamer.Strings;
import com.lk.openstreamer.log.Logger;

import java.nio.ByteBuffer;

/**
 * Holds encoded video data in a circular buffer.
 * <p>
 * This is actually a pair of circular buffers, one for the raw data and one for the meta-data
 * (flags and PTS).
 * <p>
 * Not thread-safe.
 */
class CircularEncoderBuffer {
    private static final boolean EXTRA_DEBUG = true;
    private static final boolean VERBOSE = false;

    // Raw data (e.g. AVC NAL units) held here.
    //
    // The MediaMuxer writeSampleData() function takes a ByteBuffer.  If it's a "direct"
    // ByteBuffer it'll access the data directly, if it's a regular ByteBuffer it'll use
    // JNI functions to access the backing byte[] (which, in the current VM, is done without
    // copying the data).
    //
    // It's much more convenient to work with a byte[], so we just wrap it with a ByteBuffer
    // as needed.  This is a bit awkward when we hit the edge of the buffer, but for that
    // we can just do an allocation and data copy (we know it happens at most once per file
    // save operation).
    private final ByteBuffer dataBufferWrapper;
    private final byte[] dataBuffer;

    // Meta-data held here.  We're using a collection of arrays, rather than an array of
    // objects with multiple fields, to minimize allocations and heap footprint.
    private final int[] packetFlags;
    private final long[] packetPtsUsec;
    private final int[] packetStart;
    private final int[] packetLength;

    // Data is added at head and removed from tail.  Head points to an empty node, so if
    // head==tail the list is empty.
    private int metaHead;
    private int metaTail;

    /**
     * Allocates the circular buffers we use for encoded data and meta-data.
     */
    public CircularEncoderBuffer(int bitRate, int frameRate, int desiredSpanSec) {
        // For the encoded data, we assume the encoded bit rate is close to what we request.
        //
        // There would be a minor performance advantage to using a power of two here, because
        // not all ARM CPUs support integer modulus.
        int dataBufferSize = bitRate * desiredSpanSec / 8;
        dataBuffer = new byte[dataBufferSize];
        dataBufferWrapper = ByteBuffer.wrap(dataBuffer);

        // Meta-data is smaller than encoded data for non-trivial frames, so we over-allocate
        // a bit.  This should ensure that we drop packets because we ran out of (expensive)
        // data storage rather than (inexpensive) metadata storage.
        int metaBufferCount = frameRate * desiredSpanSec * 2;
        packetFlags = new int[metaBufferCount];
        packetPtsUsec = new long[metaBufferCount];
        packetStart = new int[metaBufferCount];
        packetLength = new int[metaBufferCount];

        if (VERBOSE) {
            Logger.d(Strings.CBE_BIT_RATE + bitRate + Strings.FRAME_RATE + frameRate +
                    Strings.DESIRED_SPAN + desiredSpanSec + Strings.DATA_BUFFER_SIZE + dataBufferSize +
                    Strings.META_BUFFER_COUNT + metaBufferCount);
        }
    }

    /**
     * Computes the amount of time spanned by the buffered data, based on the presentation
     * time stamps.
     */
    public long computeTimeSpanUsec() {
        final int metaLen = packetStart.length;

        if (metaHead == metaTail) {
            // empty list
            return 0;
        }

        // head points to the next available node, so grab the previous one
        int beforeHead = (metaHead + metaLen - 1) % metaLen;
        return packetPtsUsec[beforeHead] - packetPtsUsec[metaTail];
    }

    /**
     * Adds a new encoded data packet to the buffer.
     *
     * @param buf The data.  Set position() to the start offset and limit() to position+size.
     *     The position and limit may be altered by this method.
     * @param flags MediaCodec.BufferInfo flags.
     * @param ptsUsec Presentation time stamp, in microseconds.
     */
    public void add(ByteBuffer buf, int flags, long ptsUsec) {
        int size = buf.limit() - buf.position();
        if (VERBOSE) {
            Logger.d(Strings.ADD_SIZE + size + Strings.FLAGS_0_X + Integer.toHexString(flags) +
                    Strings.PTS + ptsUsec);
        }
        while (!canAdd(size)) {
            removeTail();
        }

        final int dataLen = dataBuffer.length;
        final int metaLen = packetStart.length;
        int packetStart = getHeadStart();
        packetFlags[metaHead] = flags;
        packetPtsUsec[metaHead] = ptsUsec;
        this.packetStart[metaHead] = packetStart;
        packetLength[metaHead] = size;

        // Copy the data in.  Take care if it gets split in half.
        if (packetStart + size < dataLen) {
            // one chunk
            buf.get(dataBuffer, packetStart, size);
        } else {
            // two chunks
            int firstSize = dataLen - packetStart;
            if (VERBOSE) {
                Logger.v(Strings.SPLIT_FIRSTSIZE + firstSize + Strings.SIZE + size);
            }
            buf.get(dataBuffer, packetStart, firstSize);
            buf.get(dataBuffer, 0, size - firstSize);
        }

        metaHead = (metaHead + 1) % metaLen;

        if (EXTRA_DEBUG) {
            // The head packet is the next-available spot.
            packetFlags[metaHead] = 0x77aaccff;
            packetPtsUsec[metaHead] = -1000000000L;
            this.packetStart[metaHead] = -100000;
            packetLength[metaHead] = Integer.MAX_VALUE;
        }
    }

    /**
     * Returns the index of the oldest sync frame.  Valid until the next add().
     * <p>
     * When sending output to a MediaMuxer, start here.
     */
    public int getFirstIndex() {
        final int metaLen = packetStart.length;

        int index = metaTail;
        while (index != metaHead) {
            //noinspection deprecation
            if ((packetFlags[index] & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0) {
                break;
            }
            index = (index + 1) % metaLen;
        }

        if (index == metaHead) {
            Logger.w(Strings.HEY_COULD_NOT_FIND_SYNC_FRAME_IN_BUFFER);
            index = -1;
        }
        return index;
    }

    /**
     * Returns the index of the next packet, or -1 if we've reached the end.
     */
    public int getNextIndex(int index) {
        final int metaLen = packetStart.length;
        int next = (index + 1) % metaLen;
        if (next == metaHead) {
            next = -1;
        }
        return next;
    }

    /**
     * Returns a reference to a "direct" ByteBuffer with the data, and fills in the
     * BufferInfo.
     * <p>
     * The caller must not modify the contents of the returned ByteBuffer.  Altering
     * the position and limit is allowed.
     */
    public ByteBuffer getChunk(int index, MediaCodec.BufferInfo info) {
        final int dataLen = dataBuffer.length;
        int packetStart = this.packetStart[index];
        int length = packetLength[index];

        info.flags = packetFlags[index];
        info.offset = packetStart;
        info.presentationTimeUs = packetPtsUsec[index];
        info.size = length;

        if (packetStart + length <= dataLen) {
            // one chunk, return full buffer to avoid copying data
            return dataBufferWrapper;
        } else {
            // two chunks
            ByteBuffer tempBuf = ByteBuffer.allocateDirect(length);
            int firstSize = dataLen - packetStart;
            tempBuf.put(dataBuffer, this.packetStart[index], firstSize);
            tempBuf.put(dataBuffer, 0, length - firstSize);
            info.offset = 0;
            return tempBuf;
        }
    }

    /**
     * Computes the data buffer offset for the next place to store data.
     * <p>
     * Equal to the start of the previous packet's data plus the previous packet's length.
     */
    private int getHeadStart() {
        if (metaHead == metaTail) {
            // list is empty
            return 0;
        }

        final int dataLen = dataBuffer.length;
        final int metaLen = packetStart.length;

        int beforeHead = (metaHead + metaLen - 1) % metaLen;
        return (packetStart[beforeHead] + packetLength[beforeHead] + 1) % dataLen;
    }

    /**
     * Determines whether this is enough space to fit "size" bytes in the data buffer, and
     * one more packet in the meta-data buffer.
     *
     * @return True if there is enough space to add without removing anything.
     */
    private boolean canAdd(int size) {
        final int dataLen = dataBuffer.length;
        final int metaLen = packetStart.length;

        if (size > dataLen) {
            throw new RuntimeException("Enormous packet: " + size + " vs. buffer " +
                    dataLen);
        }
        if (metaHead == metaTail) {
            // empty list
            return true;
        }

        // Make sure we can advance head without stepping on the tail.
        int nextHead = (metaHead + 1) % metaLen;
        if (nextHead == metaTail) {
            if (VERBOSE) {
                Logger.v(Strings.RAN_OUT_OF_METADATA_HEAD + metaHead + Strings.TAIL + metaTail + ")");
            }
            return false;
        }

        // Need the byte offset of the start of the "tail" packet, and the byte offset where
        // "head" will store its data.
        int headStart = getHeadStart();
        int tailStart = packetStart[metaTail];
        int freeSpace = (tailStart + dataLen - headStart) % dataLen;
        if (size > freeSpace) {
            if (VERBOSE) {
                Logger.v(Strings.RAN_OUT_OF_DATA_TAIL_START + tailStart + Strings.HEAD_START + headStart +
                        Strings.REQ + size + Strings.FREE + freeSpace + ")");
            }
            return false;
        }

        if (VERBOSE) {
            Logger.v(Strings.OK_SIZE + size + Strings.FREE1 + freeSpace + Strings.META_FREE +
                    ((metaTail + metaLen - metaHead) % metaLen - 1));
        }

        return true;
    }

    /**
     * Removes the tail packet.
     */
    private void removeTail() {
        if (metaHead == metaTail) {
            throw new RuntimeException(Strings.CAN_T_REMOVE_TAIL_IN_EMPTY_BUFFER);
        }
        final int metaLen = packetStart.length;
        metaTail = (metaTail + 1) % metaLen;
    }
}
