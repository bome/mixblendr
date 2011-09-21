/*
 * $Id: $
 *
 * (c) by Bome Software
 * All rights reserved.
 */

package com.mixblendr.util;

import org.w3c.dom.Element;

/**
 * An interface for classes that can export and import itself via xml.
 * 
 * @author Florian Bomers
 */
public interface XmlPersistent {

	/**
	 * export the implementing class to a child element of element.
	 * 
	 * @return the (first) child element that was added to the given element, or
	 *         the given element itself if no child element was added.
	 */
	Element xmlExport(Element element);

	/** import the implementing class from this element */
	void xmlImport(Element element) throws Exception;
}
