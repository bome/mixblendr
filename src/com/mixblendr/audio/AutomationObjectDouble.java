/*
 * $Id: $
 *
 * (c) by Bome Software
 * All rights reserved.
 */

package com.mixblendr.audio;

import org.w3c.dom.Element;

/**
 * An automation object with just one single value (with double precision)
 * 
 * @author Florian Bomer
 */
public abstract class AutomationObjectDouble extends AutomationObject {

	private String xmlName;
	
	protected volatile double value;
	
	public AutomationObjectDouble(AudioState state, String xmlName, long startTime, double value) {
		super(state, startTime);
		this.xmlName = xmlName;
		this.value = value;
	}
	
	
	
	/*
	 * (non-Javadoc)
	 * @see com.mixblendr.audio.AutomationObject#xmlExport(org.w3c.dom.Element)
	 */
	/**
	 * @return the xmlName
	 */
	public String getXmlName() {
		return xmlName;
	}

	/**
	 * @return the value
	 */
	public double getValue() {
		return value;
	}


	// PERSISTENCE
	
	@Override
	public Element xmlExport(Element element) {
		element = super.xmlExport(element, xmlName);
		// only export 5 digits
		double rounded = Math.round(value * 10000.0) / 10000.0; 
		element.setAttribute("Value", String.valueOf(rounded));
		return element;
	}

	
	/*
	 * (non-Javadoc)
	 * @see com.mixblendr.audio.AutomationObject#xmlImport(org.w3c.dom.Element)
	 */
	@Override
	public void xmlImport(Element element) throws Exception {
		assert(owner != null);
		element = super.xmlImport(element, xmlName);
		value = Double.parseDouble(element.getAttribute("Value"));
	}

}
