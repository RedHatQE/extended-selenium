package com.redhat.qe.auto.selenium;

import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.redhat.qe.jul.TestRecords;
import com.thoughtworks.selenium.CommandProcessor;
import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.SeleniumException;

/**
 * This class extends the DefaultSelenium functionality.  It 
 * provides logging of UI actions (via java standard logging),
 * and some convenience methods.
 * @author jweiss
 *
 */
public class ExtendedSelenium extends DefaultSelenium implements ITestNGScreenCapture, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4832886620261520916L;

	private static ExtendedSelenium instance = null;
	
	private static Logger log = Logger.getLogger(ExtendedSelenium.class.getName());
	private static File screenshotDir = null;
	private static File localHtmlDir = null;
	private static final DecimalFormat numFormat = new DecimalFormat("##0.#");
	protected static final String DEFAULT_WAITFORPAGE_TIMEOUT = "60000";
	protected static String WAITFORPAGE_TIMEOUT = DEFAULT_WAITFORPAGE_TIMEOUT;
	private DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmssS");
	protected String ajaxFinishedCondition = null;
	public static final String JQUERY_AJAX_FINISHED_CONDITION = 
			"try { selenium.browserbot.getCurrentWindow().jQuery.active == 0 } catch (e) { false }";
	public static final String PROTOTYPE_AJAX_FINISHED_CONDITION = "selenium.browserbot.getCurrentWindow().Ajax.activeRequestCount == 0";
	public static final String DOJO_AJAX_FINISHED_CONDITION = "selenium.browserbot.getCurrentWindow().dojo.io.XMLHTTPTransport.inFlight.length == 0";
	

	public ExtendedSelenium(CommandProcessor processor) {
		super(processor);

	}

	public ExtendedSelenium(String serverHost, int serverPort,
			String browserStartCommand, String browserURL) {
		super(serverHost, serverPort, browserStartCommand, browserURL);
	}

	@Override
	public void start() {
		log.finer("Start selenium.");
		super.start();

		windowFocus();
		windowMaximize();
		String delay = System.getProperty("selenium.delay");
		if (delay != null)  {
			try {
				setSpeed(delay);
			}
			catch(Exception e){
				log.log(Level.FINER, "Could not set delay: " + delay, e);
			}
		}
	}
	

	@Override
	public void stop() {
		log.finer("Stop selenium.");
		super.stop();
		//added this as part of a fix to guarantee that only instance of selenium
		//is running.  So be sure that there is only one browser session up at a time
		killInstance();
		
		//debugging this, because screenshots are getting taken too late on hudson wdh
		//log.fine("Selenium stopped.");
		//log.log(MyLevel.FINER,"Selenium stopped");
		//log.info("Selenium stopped");

	}
	
	
	public boolean isEditable(Element element) {
		return super.isEditable(element.getLocator());
	}

	public boolean isChecked(Element element) {
		return super.isChecked(element.getLocator());
	}

	public String getValue(Element element) {
		return getValue(element.getLocator());
	}
	
	@Override
	public String getValue(String locator) {
		highlight(locator);
		return super.getValue(locator);
	}

	public void clickAndWait(String locator) {
		clickAndWait(locator, WAITFORPAGE_TIMEOUT, true);
		ajaxWait();
	}
	
	public void clickAndWait(Element element) {
		click(element);
		waitForPageToLoad(WAITFORPAGE_TIMEOUT);
		ajaxWait();
	}

		
	public void clickAndWait(String locator, String timeout) {
		clickAndWait(locator, timeout, true);
	}

	public void clickAndWait(Element element, String timeout) {
		clickAndWait(element.getLocator(), timeout);
	}

	public void clickAndWait(String locator, String timeout, boolean highlight) {
		click(locator, highlight);
		waitForPageToLoad(timeout);
		ajaxWait();
	}
	
	public void clickAndWait(Element element, String timeout, boolean highlight) {
		clickAndWait(element.getLocator(), timeout, highlight);
	}

	public void selectAndWait(String selectLocator, String optionLocator){
		select(selectLocator, optionLocator);
		waitForPageToLoad();
	}
	
	public void selectAndWait(Element element, String optionLocator){
		selectAndWait(element.getLocator(), optionLocator);
	}
	
	public void waitForPageToLoad(){
		waitForPageToLoad(WAITFORPAGE_TIMEOUT);
	}
	
	@Override
	public void waitForPageToLoad(String timeout){
		log.finer("Wait for page to load.");
		long start = System.currentTimeMillis();
		super.waitForPageToLoad(timeout);
		ajaxWait();
		Double waitedInSecs = ((System.currentTimeMillis() - start)) / 1000.0;
		
		log.finer("Waited " + numFormat.format(waitedInSecs) + "s for page to load.");

	}
	/**
	 * @param locator
	 * @param highlight - if true, highlight the element for a fraction of a second before clicking it.
	 *   This makes it easier to see what selenium is doing "live".
	 */
	public void click(String locator, boolean highlight)  {
		log.log(Level.INFO, "Click on " + getDescription(locator), TestRecords.Style.Action);
		if (highlight) highlight(locator);
		super.click(locator);
		ajaxWait();
	}
	
	public void doubleClick(String locator, boolean highlight)  {
		log.log(Level.INFO, "Double click on " + getDescription(locator), TestRecords.Style.Action);
		if (highlight) highlight(locator);
		super.doubleClick(locator);
		ajaxWait();
	}

	@Override
	public void click(String locator) {
		click(locator, true);
	}
	
	@Override
	public void doubleClick(String locator) {
		doubleClick(locator, true);
	}
	
	//TODO need to have logging like this on all similar methods  --JMW 10/5/09
	public void click(Element element) {
		Element humanReadable = element.getHumanReadable();
		if (humanReadable != null) {
			try {
				log.log(Level.INFO, "Click on element: " + this.getText(humanReadable), TestRecords.Style.Action);
			} catch(Exception e) {
				log.log(Level.FINEST, "Unable to get text for associated human readable element: " + humanReadable, e);
			}		
		} else {
			log.log(Level.INFO, "Click on " + getDescription(element), TestRecords.Style.Action);
		}
	    highlight(element);
		super.click(element.getLocator());
		ajaxWait();
	}
	
	public void doubleClick(Element element) {
		Element humanReadable = element.getHumanReadable();
		if (humanReadable != null) {
			try {
				log.log(Level.INFO, "Double click on element: " + this.getText(humanReadable), TestRecords.Style.Action);
			} catch(Exception e) {
				log.log(Level.FINEST, "Unable to get text for associated human readable element: " + humanReadable, e);
			}		
		} else {
			log.log(Level.INFO, "Double click on " + getDescription(element), TestRecords.Style.Action);
		}
		highlight(element);
		super.doubleClick(element.getLocator());
		ajaxWait();
	}
	
	public String getText(Element element){
		return getText(element.getLocator());
	}
	
	@Override
	public String getText(String locator) {
		highlight(locator);
		return super.getText(locator);
	}
	
	public String getSelectedLabel(Element element){
		return getSelectedLabel(element.getLocator());
	}
	
	@Override
	public void mouseOver(String locator) {
		log.log(Level.INFO, "Hover over " + getDescription(locator), TestRecords.Style.Action);
		super.mouseOver(locator);

	}
	
	public void mouseOver(Element element) {
		log.log(Level.INFO, "Hover over " + getDescription(element), TestRecords.Style.Action);
		super.mouseOver(element.getLocator());
	}

	@Override
	public void keyPress(String locator, String keySequence) {
		highlight(locator);
		super.keyPress(locator,keySequence);
	}
	
	public void keyPress(Element element, String keySequence) {
		log.log(Level.INFO, "Press and release key '"+keySequence+"' on " + getDescription(element), TestRecords.Style.Action);
		keyPress(element.getLocator(), keySequence);
	}

	/**
	 * @param locator
	 * @param highlight - if true, highlight the element for a fraction of a second before clicking it.
	 *   This makes it easier to see what selenium is doing "live".
	 */
	public void click(String locator, String humanReadableName, boolean highlight) {
		log.log(Level.INFO, "Click on : " + humanReadableName, TestRecords.Style.Action);
		if (highlight) highlight(locator);
		super.click(locator);
		ajaxWait();
	}


	public void click(String locator, String humanReadableName) {
		click(locator,humanReadableName, true);
	}
	
	
	/**
	 * Waits for an element to appear on the page, then clicks it.  This method is useful for interacting with 
	 * elements that are created by AJAX calls.  You should be reasonably sure that the element will in fact appear,
	 * otherwise the execution will not continue until the timeout is hit (and then an exception will be thrown).
	 * @param locator A locator for the element to click on when it appears
	 * @param timeout How long to wait for the element to appear before timing out and throwing an exception
	 */
	public void waitAndClick(String locator, String timeout){
		super.waitForCondition("selenium.isElementPresent(\"" + escape(locator) + "\");", timeout);
		click(locator);
	}
	
	public void waitAndClick(Element element, String timeout) {
		super.waitForCondition("selenium.isElementPresent(\"" + escape(element.getLocator()) + "\");", timeout);
		click(element);
	}

	
	/**
	 * Similar to waitAndClick-  waits for an element to appear on the page, then clicks it, then waits for the page
	 * to load. 
	 * @param locator A locator for the element to click on when it appears
	 * @param timeout1 How long to wait for the element to appear before timing out and throwing an exception
	 * @param timeout2 How long to wait for the page to load after clicking the element.
	 */
	public void waitAndClickAndWait(String locator, String timeout1, String timeout2){
		try {
			super.waitForCondition("selenium.isElementPresent(\"" + escape(locator) + "\");", timeout1);
		}
		catch(Exception e){
			RuntimeException rte = new RuntimeException("Element did not appear: " + locator);
			rte.initCause(e);
			throw rte;
		}
		clickAndWait(locator, timeout2);
	}
	
	public void waitAndClickAndWait(Element element, String timeout1, String timeout2) {
		waitAndClickAndWait(element.getLocator(), timeout1, timeout2);
	}

	
	public void waitAndClickAndWait(String locator, String timeout1){
		waitAndClickAndWait(locator, timeout1, WAITFORPAGE_TIMEOUT);
	}
	
	public void waitAndClickAndWait(Element element, String timeout1) {
		waitAndClickAndWait(element.getLocator(), timeout1);
	}
	
	public void waitForElement(String locator, String timeout){
		log.info("Waiting for element '" + locator  + "', with timeout of " + timeout + ".");
		super.waitForCondition("selenium.isElementPresent(\"" + escape(locator) + "\");", timeout);
	}
	
	public void waitForElement(Element element, String timeout){
		log.info("Wait for element '" + element  + "', with timeout of " + timeout + ".");
		super.waitForCondition("selenium.isElementPresent(\"" + escape(element.getLocator()) + "\");", timeout);
	}
	
	public void waitForTextPresent(String text, String timeout) {
		log.info("Wait for text `" + text + "`, with timeout of " + timeout + ".");
		super.waitForCondition("selenium.isTextPresent(\"" + text +"\");", timeout);
	}
	
	/**
	 * Wait for an element to be invisible.<br>
	 * Note: On an AJAXy page, an element can exist and be invisible.
	 * @param locator - element locator path to wait for
	 * @param timeout - milliseconds
	 * @author jsefler
	 */
	public void waitForInvisible(String locator, String timeout){
		// if the locator is not present, then it is effectively invisible
		if (!super.isElementPresent(locator)) return;
		log.finer("Wait for element to be invisible '"+locator+"', with timeout of "+timeout+".");
		try{
			super.waitForCondition("!selenium.isVisible(\""+ escape(locator) +"\");", timeout);
		}
		catch(SeleniumException e){
			if (e.getMessage().contains("not found")){
				log.finest("Element '"+locator+"' is not present; effectively this constitutes invisibility");
				return;
			}
			else
				throw e;
		}
	}
	
	public void waitForInvisible(Element element, String timeout){
		waitForInvisible(element.getLocator(), timeout);
	}
	
	/**
	 * Wait for an element to be visible.<br>
	 * Note: On an AJAXy page, an element can exist and be invisible.
	 * @param locator - element locator path to wait for
	 * @param timeout - milliseconds
	 * @author jsefler
	 */
	public void waitForVisible(String locator, String timeout){
		log.finer("Wait for element to be visible '" + locator  + "', with timeout of " + timeout + ".");
		super.waitForCondition("selenium.isElementPresent(\""+ escape(locator) +"\");", timeout); // first wait for its existence
		super.waitForCondition("selenium.isVisible(\""+ escape(locator) +"\");", timeout); // then wait for its visibility
	}
	
	public void waitForVisible(Element element, String timeout){
		waitForVisible(element.getLocator(), timeout);
	}
	
	@Override
	public void type(String locator, String value) {
		log.log(Level.INFO, "Type '" + value + "' into " + getDescription(locator), TestRecords.Style.Action);
		highlight(locator);
		super.type(locator, value);
	}
	
	public void type(Element element, String value) {
		type(element.getLocator(), value);
	}
	
	@Override
	public void typeKeys(String locator, String value) {
		log.log(Level.INFO, "Type keys '" + value + "' into " + getDescription(locator), TestRecords.Style.Action);
		highlight(locator);
		super.typeKeys(locator, value);
	}
	
	public void typeKeys(Element element, String value) {
		typeKeys(element.getLocator(), value);
	}
	
	public void type(String locator, String humanReadableName, String value) {
		log.log(Level.INFO, "Type '" + value + "' into " + getElementType(locator) + ": " + humanReadableName + "'", TestRecords.Style.Action);
		highlight(locator);
		super.type(locator, value);
		ajaxWait();
	}
	
	public void setText(String locator, String value){
		type(locator, value);
	}
	
	public void setText(Element element, String value){
		log.log(Level.INFO, "Type '" + value + "' into " + getDescription(element), TestRecords.Style.Action);
		highlight(element);
		super.type(element.getLocator(), value);
		ajaxWait();
	}
	
	public void setText(String locator, String humanReadableName,String value){
		type(locator,humanReadableName, value);
	}
	

	
	public void open(String url, boolean ignoreSSLError) {
		final String SSLUnderstandRisks = "//*[normalize-space(@id)='expertContentHeading']";
		final String SSLExceptionAccept = "//*[normalize-space(@id)='exceptionDialogButton']";
		final String tab = new Integer(KeyEvent.VK_TAB).toString();
		final String space = new Integer(KeyEvent.VK_SPACE).toString();
		try {
			log.log(Level.INFO, "Open URL '" + url + "'.", TestRecords.Style.Action);  
			super.open(url);
			log.info("Current URL is " + getLocation() + " .");	
		}
		catch(SeleniumException se){
			if (!ignoreSSLError) throw se;
			
			if(isVisible(SSLUnderstandRisks)){
				click(SSLUnderstandRisks);
				windowFocus();
			}
			
			if(isElementPresent(SSLExceptionAccept)){
				log.info("Confirming security exception in browser.");
				for (String k: new String[] {tab, tab, space})
					keyPressNative(k);
				sleep(5000);
				windowFocus();
				for (String k: new String[] {tab, tab, tab, tab, space})
					keyPressNative(k);				
			}
		}
	}

	@Override
	public void open(String url) {
		open(url, true);
	}
	
	public void open(String url, String ajaxFinishedCondition) {
		setAjaxFinishedCondition(ajaxFinishedCondition);
		open(url, true);
	}

	@Override
	public void check(String locator) {
		log.log(Level.INFO, "Check " + getDescription(locator), TestRecords.Style.Action);
		checkUncheck(locator, true);
	}
	
	public void check(Element element){
		checkUncheck(element, true);
	}
	
	@Override
	public void uncheck(String locator) {
		log.log(Level.INFO, "Uncheck " + getDescription(locator), TestRecords.Style.Action);
		checkUncheck(locator, false);
	}
	
	public void uncheck(Element element){
		checkUncheck(element, false);
	}
	
	public void checkUncheck(Element element, boolean check){
		log.log(Level.INFO, (check? "Check ":"Uncheck ") + element, TestRecords.Style.Action);
		checkUncheck(element.getLocator(), check);
	}
	
	public void checkUncheck(String locator, boolean check){
		if (isChecked(locator) != check) {
			highlight(locator);
			super.click(locator);
			if (isChecked(locator) != check) {
				if (check) 
					super.check(locator); //just to be sure
				else super.uncheck(locator);
			}
			ajaxWait();
		}
		else {
			highlight(locator);
			log.log(Level.FINE, getDescription(locator) + " is already " + (check ? "checked.": "unchecked."));
		}
	}

	@Override
	public void select(String selectLocator, String optionLocator) {
		log.log(Level.INFO, "Select option '"	+ optionLocator + "' in list '" + selectLocator + "'.", TestRecords.Style.Action);
		highlight(selectLocator);
		super.select(selectLocator, optionLocator);
		ajaxWait();
	}
	
	public void select(Element element, String optionLocator) {
		Element humanReadable = element.getHumanReadable();
		if (humanReadable != null) {
			try {
				log.log(Level.INFO, "Select option '"	+ optionLocator + "' in list corresponding to " + getText(humanReadable), TestRecords.Style.Action);
			} catch(Exception e) {
				log.log(Level.FINEST, "Unable to get text for associated human readable element: " + humanReadable, e);
			}		
		} else {
			log.log(Level.INFO, "Select option '"	+ optionLocator + "' in list " + element, TestRecords.Style.Action);
		}
		highlight(element);
		super.select(element.getLocator(), optionLocator);
		ajaxWait();
	}
	
	/**
	 * Selects a list item by value.  To be used when a select list doesn't have any other 
	 * good locators.  It's up to the
	 * caller to make sure there isn't more than one select list on the page that contains 
	 * the same value.
	 * @param value  The value attribute of the Option.  This is not necessarily the same as what text 
	 * appears in the browser.
	 */
	public void select(String value){
		select("//select[option[@value='" + value + "']]", "value=" + value);
	}
	
	public void select(Element element){
		select(element.getLocator());
	}


	/*
	 * @see com.thoughtworks.selenium.DefaultSelenium#isElementPresent(java.lang.String)
	 */
	@Override
	public boolean isElementPresent(String element){
		return isElementPresent(element, Level.FINER);
	}
	
	public boolean isElementPresent(Element element){
		return isElementPresent(element.getLocator());
	}
	
	public boolean isVisible(Element element){
		return isVisible(element.getLocator());
	}
	
	public boolean isElementPresent(TabElement element){
		return (isElementPresent(element.getLocator()) |
				isElementPresent(element.getSelectedElement().getLocator()));
	}
	
	public boolean isElementSelected(TabElement tabElement) {
		if (tabElement.getSelectedElement().equals(tabElement)) {
			log.log(Level.WARNING, "Do not know how to determine if this tab element is selected: "+this);
			return false;
		}
		return isElementPresent(tabElement.getSelectedElement().getLocator()) && !isElementPresent(tabElement.getLocator());
	}
	
	public boolean isElementPresent(String element,Level level){
		if(super.isElementPresent(element)){
			log.log(level,"Found " + getDescription(element));
			//highlight(element); //TODO It's misleading to highlight an element on an arbitrary query.  It's more appropriate to highlight on a assertElementIsPresent(...) which is not yet written.  (jsefler 11/12/09)
			return true;
		}
		else {	
			log.log(level, "Did not find " + getDescription(element));
			return false;
		}
	}
	
	public boolean isElementPresent(Element element,Level level){
		return isElementPresent(element.getLocator(), level);
	}
	
	public boolean isTextPresent(String txt, Level level){
		if(super.isTextPresent(txt)){
			log.log(level,"Found text: '"+ txt+"'");
			return true;
		}
		else {	
			log.log(level, "Did not find text: '"+ txt+"'");
			return false;
		}
	}
	
	@Override
	public void goBack(){
		log.log(Level.INFO, "Click Browser Back Button", TestRecords.Style.Action);
		super.goBack();
		waitForPageToLoad();
	}
	
	@Override
	public void refresh(){
		log.log(Level.INFO, "Click Browser Refresh Button", TestRecords.Style.Action);
		super.refresh();
		waitForPageToLoad();
	}
	

	public boolean isElementPresentWithRefreshing(String locator, long timeout_ms, long refreshInterval_ms){
		if (isElementPresent(locator))
			return true;
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < timeout_ms ){
			sleep(refreshInterval_ms);
			refresh();
		
			if (isElementPresent(locator))
				return true;
		}
		return false;
	}
	
	public boolean isElementPresentWithRefreshing(Element element, long timeout_ms, long refreshInterval_ms){
		return isElementPresentWithRefreshing(element.getLocator(), timeout_ms, refreshInterval_ms);
	}

	@Override
	public String getAlert() {
		log.log(Level.INFO, "Click OK on alert dialog.", TestRecords.Style.Action);
		String text = super.getAlert();
		log.log(Level.INFO, "Dismissed alert dialog: " + text);
		return text;
	}

	@Override
	public String getConfirmation() {
		log.log(Level.INFO, "Click OK on confirmation dialog.", TestRecords.Style.Action);
		String text = super.getConfirmation();
		log.log(Level.INFO, "Dismissed confirmation dialog: " + text);
		return text;
	}

	@Override
	public String getPrompt() {
		log.log(Level.INFO, "Click OK on prompt dialog.", TestRecords.Style.Action);
		String text = super.getPrompt();
		log.log(Level.INFO, "Dismissed prompt dialog: " + text);
		return text;
	}
	
	@Override
	public void answerOnNextPrompt(String answer){
		log.log(Level.INFO, "Answering prompt with: " + answer, TestRecords.Style.Action);
		super.answerOnNextPrompt(answer);
	}
	
	@Override
	public void setTimeout(String timeout){
		super.setTimeout(timeout);
		WAITFORPAGE_TIMEOUT = timeout;
	}
	
	/**
	 * Retrieves the current value for Selenium wait for page timeout. 
	 * @return Current value of: WAITFORPAGE_TIMEOUT
	 */
	public String getTimeout(){
		return WAITFORPAGE_TIMEOUT;
	}
	
	public void selectPopupWindowAndWait(){
		String[] winnames = getAllWindowNames();
		String name = winnames[winnames.length-1]; //select last opened window
		waitForPopUp(name, "60000");
		selectWindow(name);
	}

	public void sleep(long millis){
		try {
			log.log(Level.INFO, "Sleep for " + millis + "ms.");
			Thread.sleep(millis);
		}
		catch(InterruptedException ie){
			log.log(Level.WARNING, "Sleep interrupted!", ie);
		}
	}
	
	public String[] getSelectOptions(Element element){
		return super.getSelectOptions(element.getLocator());
	}
	
	/**
	 * Gets the HTML attributes for a given locator
	 * @param locator 
	 * @return a Properties object containing all the attributes of the 
	 * element.  Also includes a "tagName" attribute which contains the tag name,
	 * eg, input, a, div, etc.
	 * @throws IOException
	 */
	public Properties getAttributes(String locator) {
		String attributesScript =
			"{" +
				"var elem =  this.browserbot.findElement(\"" + locator + "\");" +
				"var attrs = elem.attributes;" +
				"var str='tagName=' + elem.tagName + '\\n';" +
				"for(var i = 0; i < attrs.length; i++) {" +
				"  	str = str + attrs[i].name + '=' + attrs[i].value + '\\n';" +
				"};" +
				"str;" +  // the value of str is the returned String result from getEval(attributesScript);
			"}";
		Properties props = new Properties();
		String result = getEval(attributesScript);
		StringBuffer StringBuffer1 = new StringBuffer(result);
		try {
			ByteArrayInputStream Bis1 = new ByteArrayInputStream(StringBuffer1.toString().getBytes("UTF-8"));
			props.load(Bis1);
		}catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		return props;
	}
	
	/*
	 * Properties attr = sel().getAttributes(element);
			Set<String> mySet = attr.stringPropertyNames();
			for(String s:mySet){
				log.info(s);
				log.info(attr.getProperty(s));
			}
			log.info("TAG "+sel().getAttributes(element).getProperty("tagName"));
	 */
	public Properties getAttributes(Element element) {
		return getAttributes(element.getLocator());
	}
	public String getElementType(Element element) {
		return getElementType(element.getLocator());
	}
	
	/**
	 * Return a string description of what was acted upon by selenium
	 * @param element
	 * @return
	 */
	public String getDescription(Element element) {
		String elementStr = element.toString();
		String elementType = "";
		try {
			 elementType = getElementType(element);
		}catch(Exception e) {
			log.log(Level.FINER, "Could not retrieve element type, perhaps it is not present: " + elementStr, e);
		}
		//remove duplicate element type strings to avoid logs like... Click on link: link in table... ->  Click on link: in table...
		elementStr=elementStr.replaceAll("^" +Pattern.quote(elementType) + " ", ""); 

		//remove duplicate element type strings to avoid logs like... Click on submit button: button in table... ->  Click on submit button: in table...  (jsefler 1/7/2010)
		if (elementType.contains(" ")) elementStr=elementStr.replaceAll("^" +Pattern.quote(elementType.substring(elementType.lastIndexOf(" ")).trim()) + " ", ""); 

		return elementType + ": " + elementStr;
	}
	
	public String getDescription(String locator) {
		try {
			return getElementType(locator) + ": " + locator;
		} catch(Exception e) {
			log.log(Level.FINER, "Could not get element type for '" + locator + "', perhaps it is not present?", e);
		}
		return locator;
	}
	
	
	
	public String getElementType(String locator) {
		
		Properties attrs;
		try {
			attrs = getAttributes(locator);
		}
		catch (Exception e){
			//if attributes can't be retrieved, log and return the locator
			log.log(Level.FINEST, "Can't retrieve attributes for locator: " + locator, e);
			return locator;
		}
		String tagName = attrs.getProperty("tagName").toLowerCase();
		if (tagName.equals("input")) {
			String type = null;
			try {
				type = attrs.getProperty("type").toLowerCase();
			}
			catch(NullPointerException npe) {
				return "textbox";
			}
			if (type.equals("text")) return "textbox";
			if (type.equals("button")) return "button";
			if (type.equals("checkbox")) return "checkbox";
			if (type.equals("image")) return "input image";
			if (type.equals("password")) return "password textbox";
			if (type.equals("radio")) return "radio button";
			if (type.equals("submit")) return "submit button";
			return tagName + " " + type;
		}
		else if (tagName.equals("a")) return "link";
		else if (tagName.equals("select")) return "selectlist";
		else if (tagName.equals("div")) return "link";
		else if (tagName.equals("img")) return "image";
		else if (tagName.equals("td")) return "table cell";
		else if (tagName.equals("span")) return "link";
		else return tagName;	
	}
	
	
	public String screenCapture() throws Exception {
		String dirName = System.getProperty("selenium.screenshot.dir", System.getProperty("user.dir") + File.separator
		+ "screenshots");
		return screenCapture(dirName);
	}
	
	protected void writeBase64ScreenCapture(String data, File file) throws FileNotFoundException, IOException{
		byte[] pngBytes = Base64.decode(data);
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(pngBytes);
		fos.flush();
		fos.close();
	}
	public String screenCapture(String dirName) throws Exception {
		String outFileName = dateFormat.format(new Date()) + ".png";
		return screenCapture(dirName, outFileName, true);
	}
	
	public String screenCapture(String dirName, String outFileName, boolean writeHtml) throws Exception {
		String fullPathtoFile = null;
		mkdir(dirName);
		
		try {
			File htmlDir = localHtmlDir != null? localHtmlDir : screenshotDir;
			if (writeHtml) {
				writeHtmlOnError(htmlDir);
			}
			//if success use that next time
			localHtmlDir = htmlDir;
			fullPathtoFile = localHtmlDir.getCanonicalPath()+ File.separator + outFileName;
			pngRemoteScreenCapture(fullPathtoFile);
		}
		catch(Exception e ){
			log.log(Level.FINER, "Couldn't capture screenshot, trying to write to tmp dir instead.",e);
			//if this failed, try the temp dir
			screenshotDir = new File("/tmp");
			super.captureScreenshot("/tmp"+ File.separator + outFileName);
			//log.log(Level.FINE, "Captured ScreenShot to "+"/tmp"+ File.separator + outFileName);
			fullPathtoFile = "/tmp"+ File.separator + outFileName;
			
			//writeHtmlOnError(screenshotDir);		
		}
		return fullPathtoFile;
	}
	
	public String testNGScreenCapture(String className, String methodName) throws Exception{
		String dirName = System.getProperty("selenium.screenshot.dir", System.getProperty("user.dir") + File.separator
				+ "test-output" + File.separator + "screenshots");
		mkdir(dirName);
		Date rightNow = new Date();
		String outFileName = dateFormat.format(rightNow) + "-" + className + "." + methodName + ".png";
		String fullpath = dirName + File.separator + outFileName;
		pngRemoteScreenCapture(fullpath);
		return fullpath;
	}
		
	protected void pngRemoteScreenCapture(String filepath) throws Exception{
		String base64Png = super.captureEntirePageScreenshotToString("");
		File ssFile = new File(filepath);
		writeBase64ScreenCapture(base64Png, ssFile);
		log.log(Level.FINE, "screenshot URL= "+ getLocation());
		log.log(Level.FINE, "Captured screenshot to "+ ssFile.toURI().toURL());
	}
	
	protected void mkdir(String dirName){
		if (screenshotDir == null) {
			screenshotDir = new File(dirName);
		}
		if (!(screenshotDir.exists() && screenshotDir.isDirectory())) {
			screenshotDir.mkdirs();
		}
	}
	
	
	
	protected void writeHtmlOnError(File dir) throws Exception{

		Date rightNow = new Date();
		BufferedWriter out = new BufferedWriter(new FileWriter(dir.getCanonicalPath()
				 + File.separator + dateFormat.format(rightNow) + ".html"));
		out.write(getHtmlSource());
		out.close();
	}
	
	

	public static ExtendedSelenium getInstance(){
		if (instance == null) throw new NullPointerException("Selenium instance not set yet.");
		return instance;
	}
	
	public static void killInstance(){
		instance = null;//
	}
	
	@Override
	public void highlight(String locator) {
		// TODO a decision to globally turn on/off highlight should be done here
		try {
			super.highlight(locator);
		} catch (Exception e) {
			log.log(Level.FINER, "Could not highlight locator '"+locator+"', perhaps it is not present: " + e);
		}
	}
	
	public void highlight(Element element) {
		highlight (element.getLocator());
	}
	
	public static ExtendedSelenium newInstance(String serverHost, int serverPort, String browserStartCommand, String browserURL){
		instance = new ExtendedSelenium(serverHost, serverPort, browserStartCommand, browserURL);
		return instance;
	}
	
	public static String escape(String locator){
		return locator.replace("\"", "\\\"");
	}


	public void waitForEnabled(Element elem, String millis) {
		// FIXME: Until the javascript portion of this method performs more consistently, 
		//		  sleep is a better alternative.
		/*
		log.info("Wait for enabled'" + elem + "', with timeout of " + millis + ".");
		String locatorStr = escape(elem.getLocator());
		String script = "" //
			+ "{var elem = selenium.browserbot.findElement(\""
			+ locatorStr
			+ "\");" //
			+ "var canvas = selenium.browserbot.getUserWindow().isc.AutoTest.locateCanvasFromDOMElement(elem);" //
			+ "!canvas.isDisabled();}";
		sel().waitForCondition(script, millis);
		*/
		try {
			Thread.currentThread().sleep(Integer.parseInt(millis));
		}
		catch (InterruptedException e) {
			log.log(Level.INFO, "Thread sleep error: ", e);
			e.printStackTrace();
		}
	}

	public void waitForEnabledAndClick(Element elem, String millis) {
		waitForEnabled(elem, millis);
		click(elem);
	}

	public void setAjaxFinishedCondition(String ajaxFinishedCondition) {
		this.ajaxFinishedCondition = ajaxFinishedCondition;
	}
	
	public void ajaxWait(){
		if (ajaxFinishedCondition != null) {
			waitForCondition(ajaxFinishedCondition, WAITFORPAGE_TIMEOUT);			
		}
	}
	
	public static void main (String... args) {
/*		System.out.println(Pattern.quote("^//td[(normalize-space(.)='jweiss-rhel1.usersys.redhat.com')]/..//input[@type='checkbox']"));
	
		String elementType = "submit button", elementStr="button in a table";
		if (elementType.contains(" ")) elementStr=elementStr.replaceAll("^" +Pattern.quote(elementType.substring(elementType.lastIndexOf(" ")).trim()) + " ", ""); 
*/
		ExtendedSelenium sel = new ExtendedSelenium("localhost", 4444, "", "https://smqe-sat04.lab.eng.brq.redhat.com:3000/");
		sel.start();
		sel.open("https://smqe-sat04.lab.eng.brq.redhat.com:3000/");
		
	}
}
