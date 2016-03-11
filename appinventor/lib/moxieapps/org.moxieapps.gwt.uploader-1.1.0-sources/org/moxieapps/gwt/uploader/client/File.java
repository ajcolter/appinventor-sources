/*
 * Copyright 2011 Moxie Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.moxieapps.gwt.uploader.client;

import com.google.gwt.core.client.JavaScriptObject;

import java.util.Date;

/**
 * A logical bean that provides access to the details of a file being uploaded.  An instance
 * of this object is available on all uploader related event handlers, such as the
 * {@link org.moxieapps.gwt.uploader.client.events.UploadProgressHandler}.
 * <p/>
 * Note that, by default, the SWFUpload "Speed" plugin is enabled, providing access to additional
 * metrics on this object such as {@link #getAverageSpeed()}, {@link #getPercentUploaded()}, etc.
 *
 * @author squinn@moxiegroup.com (Shawn Quinn)
 */
public final class File extends JavaScriptObject {

    /**
     * An enumeration of reported file status types, which can be determined during
     * any of the uploader events, such as {@link org.moxieapps.gwt.uploader.client.events.UploadProgressEvent#getFile()}.
     */
    public enum Status {

        UNKNOWN(0),
        QUEUED(-1),
        IN_PROGRESS(-2),
        ERROR(-3),
        COMPLETE(-4),
        CANCELLED(-5);

        private Status(int optionValue) {
            this.optionValue = optionValue;
        }

        private final int optionValue;

        public int toInt() {
            return optionValue;
        }
    }

    /**
     * Instances of the File object can not be created directly, but should instead
     * be obtained via an event handler.
     * E.g. {@link org.moxieapps.gwt.uploader.client.events.UploadProgressEvent#getFile()}.
     */
    protected File() { }

    /**
     * Return the unique id of the file instance as it is being maintained within the Uploader
     * component file queue.
     *
     * @return String
     */
    public native String getId() /*-{
        return this.id;
    }-*/;

    /**
     * Return the index of the file within the queue of files being uploaded by the Uploader component.
     *
     * @return int
     */
    public native int getIndex() /*-{
        return this.index;
    }-*/;

    /**
     * Return the original name of the file being uploaded.
     *
     * @return String
     */
    public native String getName() /*-{
        return this.name;
    }-*/;

    /**
     * Return the original size of the file being uploaded (in bytes).
     *
     * @return long
     */
    public long getSize() {
        return (long) nativeGetSize();
    }

    /**
     * Return the mime type of the file being uploaded.
     *
     * @return String
     */
    public native String getType() /*-{
        return this.type;
    }-*/;

    /**
     * Returns the date the file was created, as reported by the client machine.  This method
     * is purposefully hidden as it is available when the SWFUpload/Flash component is used, but
     * is not available when the XMLHttpRequest Level 2 approach is used (which is the default).  Please
     * use the {@link #getModificationDate()} method instead (which is available in both cases).
     *
     * Reference: http://www.w3.org/TR/file-uploader/#dfn-file
     *
     * @return Date
     */
    protected Date getCreationDate() {
        return new Date((long) nativeGetCreationDate());
    }

    /**
     * Return the date the file was last modified, as reported by the client machine, or null
     * if the browser or OS did not make the file's modification date available.
     *
     * @return Date
     */
    public Date getModificationDate() {
        long date = (long) nativeGetModificationDate();
        return date > 0 ? new Date(date) : null;
    }

    /**
     * Get the file's current status (see {@link Status} enumeration for possible values)
     *
     * @return Status
     */
    public Status getStatus() {
        switch(nativeGetStatus()) {
            case -1:
                return Status.QUEUED;
            case -2:
                return Status.IN_PROGRESS;
            case -3:
                return Status.ERROR;
            case -4:
                return Status.COMPLETE;
            case -5:
                return Status.CANCELLED;
            default:
                return Status.UNKNOWN;
        }
    }

    /**
     * Return a floating point number indicating the current uploader speed of
     * the file, in bits per second.
     *
     * @return double
     */
    public native double getCurrentSpeed() /*-{
        return this.currentSpeed ? this.currentSpeed : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the overall average uploader speed,
     * bytes per second, in bits per second.
     *
     * @return double
     */
    public native double getAverageSpeed() /*-{
        return this.averageSpeed ? this.averageSpeed : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the uploader speed averaged over the
     * last several measurements, in bits per second.
     *
     * @return double
     */
    public native double getMovingAverageSpeed() /*-{
        return this.movingAverageSpeed ? this.movingAverageSpeed : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the estimated remaining
     * uploader time, in seconds.
     *
     * @return double
     */
    public native double getTimeRemaining() /*-{
        return this.timeRemaining ? this.timeRemaining : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the number of seconds
     * passed so far for this uploader.
     *
     * @return double
     */
    public native double getTimeElapsed() /*-{
        return this.timeElapsed ? this.timeElapsed : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the percentage of the
     * file uploaded (0 to 100).
     *
     * @return double
     */
    public native double getPercentUploaded() /*-{
        return this.percentUploaded ? this.percentUploaded : 0.0;
    }-*/;

    /**
     * Return a floating point number indicating the size uploaded so far, in bytes.
     *
     * @return double
     */
    public native double getSizeUploaded() /*-{
        return this.sizeUploaded ? this.sizeUploaded : 0.0;
    }-*/;

    private native double nativeGetSize() /*-{
        return this.size ? this.size : 0.0;
    }-*/;

    private native double nativeGetCreationDate() /*-{
        return this.creationdate != null ? this.creationdate.getTime() : 0;
    }-*/;

    private native double nativeGetModificationDate() /*-{
        return this.modificationdate != null ? this.modificationdate.getTime() : 0;
    }-*/;

    private native int nativeGetStatus() /*-{
        return this.filestatus ? this.filestatus : 0;   // 0 == UNKNOWN
    }-*/;

}
