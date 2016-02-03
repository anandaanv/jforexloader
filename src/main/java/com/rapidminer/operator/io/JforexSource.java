/**
 * Copyright (C) 2001-2015 by RapidMiner and the contributors
 *
 * Complete list of developers available at our web site:
 *
 * http://rapidminer.com
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */
package com.rapidminer.operator.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.DataRow;
import com.rapidminer.example.table.DataRowReader;
import com.rapidminer.example.table.DoubleArrayDataRow;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.license.LicenseConstants;
import com.rapidminer.license.annotation.LicenseConstraint;
import com.rapidminer.license.product.ConnectorTypes;
import com.rapidminer.license.product.Product;
import com.rapidminer.operator.OperatorCreationException;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.preprocessing.GuessValueTypes;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeString;
import com.rapidminer.tools.Ontology;
import com.rapidminer.tools.OperatorService;
import com.rapidminer.tools.Tools;
import com.rapidminer.tools.io.Encoding;

import jforez.plugin.loader.DataItem;
import jforez.plugin.loader.ForexDataDownloader;


/**
 * <p>
 * This operator reads an example set from an URL. The format has to be a CSV format with ';' as
 * column separator and nominal values have to be quoted with a double quote (&quot;). A quote
 * inside of a nominal value has to be escaped by a backslash like in \&quot;. The first row is
 * allowed to contain the column names which has to be indicated by the corresponding parameter.
 * Comments are not allowed, unknown attribute values can be marked with empty strings or a question
 * mark.
 * </p>
 *
 * <p>
 * This operator is not nearly as powerful as the operators ExampleSource or SimpleExampleSource but
 * is on the other hand able to read data from arbitrary places as long as the format fits the
 * specification above. Please note also that the usage of this operator hardly allows for a correct
 * meta data description which might lead to problems if the meta data between training and test set
 * differ in a learning scenario.
 * </p>
 *
 * <p>
 * Attribute roles can not be directly set during loading but the operator ChangeAttributeRole has
 * to be used after loading in order to change the roles.
 * </p>
 *
 * @rapidminer.index url
 * @author Ingo Mierswa
 */
@LicenseConstraint(productId = Product.RM_REGEX, constraintId = LicenseConstants.CONNECTORS_CONSTRAINT_ID, value = ConnectorTypes.ADVANCED_CONNECTORS)
public class JforexSource extends AbstractExampleSource {

	public static final String PARAMETER_URL = "url";

	public static final String USER_NAME = "user_name";

	/** The parameter name for &quot;Character that is used as decimal point.&quot; */
	public static final String PASSWORD = "password";

	public static final String PARAMETER_READ_ATTRIBUTE_NAMES = "read_attribute_names";

	public static final String PARAMETER_SKIP_ERROR_LINES = "skip_error_lines";

	private static final String SYMBOL = "symbol";
	
	public static final String INTERVAL = "Interval";

	public static final String NUM_INTERVALS = "Number of intervals";

	
	private static Map<Integer, String> attrNames = new ConcurrentHashMap<>();
	
	
	static{
		int attCounter = 0;
		attrNames.put(attCounter++, "DATE");
		attrNames.put(attCounter++, "OPEN");
		attrNames.put(attCounter++, "CLOSE");
		attrNames.put(attCounter++, "HIGH");
		attrNames.put(attCounter++, "LOW");
		attrNames.put(attCounter++, "VOLUME");
	}
	
	
	
	
	public JforexSource(final OperatorDescription description) {
		super(description);
	}

	@Override
	public ExampleSet createExampleSet() throws OperatorException {
		boolean readAttributeNames = getParameterAsBoolean(PARAMETER_READ_ATTRIBUTE_NAMES);
		char decimalPointCharacter = '.';

		MemoryExampleTable table = null;
		try {
			String url= getParameter(PARAMETER_URL);
			String user= getParameter(USER_NAME);
			String password= getParameter(PASSWORD);
			String symbol= getParameter(SYMBOL);
			String interval= getParameter(INTERVAL);
			Integer numDays = Integer.valueOf(getParameter(NUM_INTERVALS));

			ForexDataDownloader downloader = new ForexDataDownloader(url, user, password, symbol, interval, numDays);

			List<DataItem> result = downloader.fetchData();

			List<Attribute> attributes = getAttrs(readAttributeNames);
			table = new MemoryExampleTable(attributes, new EntityDataRowReader(result));
			ExampleSet exampleSet = table.createExampleSet();
//			GuessValueTypes guessValuesTypes = OperatorService.createOperator(GuessValueTypes.class);
//			guessValuesTypes.setParameter(GuessValueTypes.PARAMETER_DECIMAL_POINT_CHARACTER,
//					Character.toString(decimalPointCharacter));
//			exampleSet = guessValuesTypes.apply(exampleSet);

			return exampleSet;
		} catch (Exception e) {
			throw new OperatorException("Cannot create GuessValueTypes: " + e, e);
		}
	}

	private List<Attribute> getAttrs(boolean readAttributeNames) {
		List<Attribute> attributes = new ArrayList<>();
		Set<Entry<Integer, String>> entries = attrNames.entrySet();
		for (Entry<Integer, String> entry : entries) {
			addAttr(readAttributeNames, attributes, entry.getKey(), entry.getValue());
		}
		return attributes;
	}

	@Override
	protected boolean supportsEncoding() {
		return true;
	}

	private void addAttr(boolean readAttributeNames, List<Attribute> attributes, int attCounter, String name) {
		int ontology = Ontology.NUMERICAL;
		if(name.equals("DATE")){
			ontology = Ontology.DATE;
		}
		
		if (readAttributeNames) {
			attributes.add(AttributeFactory.createAttribute(name, ontology));
		} else {
			attributes.add(AttributeFactory.createAttribute("Att" + attCounter, ontology));
		}
	}
	
	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = new LinkedList<>();

		types.add(new ParameterTypeString(PARAMETER_URL, "The url to read the data from.", false));

		ParameterType userName = new ParameterTypeString(USER_NAME,
				"User name");
		types.add(userName);

		types.add(new ParameterTypeString(PASSWORD, "password"));
		
		types.add(new ParameterTypeString(SYMBOL, "Currency Symbol"));
		
		types.add(new ParameterTypeString(INTERVAL, "Interval"));
		
		types.add(new ParameterTypeString(NUM_INTERVALS, "Number of Intervals"));
		
		
		types.addAll(super.getParameterTypes());
		return types;
	}

	
	
	
	
	private static class EntityDataRowReader implements DataRowReader{

		Iterator<DataItem> items;
		
		
		EntityDataRowReader(List<DataItem> itemsList) {
			super();
			items = itemsList.iterator();
		}

		@Override
		public boolean hasNext() {
			return items.hasNext();
		}

		@Override
		public DataRow next() {
			return new EntityDataRow(items.next());
		}
		
	}
	
	
	private static class EntityDataRow extends DataRow{

		/**
		 * 
		 */
		private static final long serialVersionUID = 5666916576594068112L;
		DataItem item;
		
		
		public EntityDataRow(DataItem item) {
			super();
			this.item = item;
		}

		@Override
		protected double get(int index, double defaultValue) {
			String name = attrNames.get(index);
			if(name == null){
				return 0;
			}
			switch(name){
			case "DATE":
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(item.getDate());
				return calendar.getTimeInMillis();
			case "OPEN":
				return item.getOpen();
			case "CLOSE":
				return item.getClose();
			case "HIGH":
				return item.getHigh();
			case "LOW":
				return item.getLow();
			case "VOLUME":
				return item.getVolume();
			}
			return 0;
		}

		@Override
		protected void set(int index, double value, double defaultValue) {
			String name = attrNames.get(index);
			if(name == null){
				return;
			}
			switch(name){
			case "DATE":
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(Double.valueOf(value).longValue());
				item.setDate(cal.getTime());
			case "OPEN":
				item.setOpen(value);
			case "CLOSE":
				item.setClose(value);
			case "HIGH":
				item.setHigh(value);
			case "LOW":
				item.setLow(value);
			case "VOLUME":
				item.setVolume(value);
			}
		}

		@Override
		protected void ensureNumberOfColumns(int numberOfColumns) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void trim() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public int getType() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
}
