/**
 *
 */
package com.mixblendr.util;

/**
 * This is a special listener which is called on fatal exceptions: e.g. the
 * audio card is not accessible anymore, an OutOfMemory exception occured, or
 * so. In general, active I/O should be ceased on reception of such a fatal
 * exception.
 * <p>
 * Note that this exception may be thrown from IO threads and inside
 * synchronized blocks, so you must switch the thread context before trying to
 * stop the threads in question.
 * 
 * @author Florian Bomers
 */
public interface FatalExceptionListener {

	/** called upon a fatal exception */
	public void fatalExceptionOccured(Throwable t, String context);

	// TODO: remove
	public void showMessage(String title, String context);

	// TODO: remove
	public void showProgressDialog();

	// TODO: remove
	public void hideProgressDialog();

	// TODO: removeSimpleAppl
	public void setSuccess(); // uploading is success
	// TODO: remove

	public void setFailed(); // uploading is failed

}
