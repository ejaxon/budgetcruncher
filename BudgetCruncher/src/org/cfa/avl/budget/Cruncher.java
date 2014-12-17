package org.cfa.avl.budget;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Reads a configuration file which lists input files, number of years and start
 * year to expect, and names of files containing translations of account code segments.
 * See sample BudgetCruncher.config file in Samples directory.
 * 
 * **WARNINGS**
 *  - File names must not contain a ':' character - I use it as a name:value separator below
 */
public class Cruncher {
	static String accountRegex = "((\\d{4})-(\\d{2})-(\\d{2})-([\\d\\w]{3})-(\\d{4})-(\\d{5})-(\\d{5})-(\\d{3})-(\\d{6}))-.*";
	static String annotationRegex = "((\\d{4})-(\\d{2})-(\\d{2})-([\\d\\w]{3})-(\\d{4})-(\\d{5})-(\\d{5})-(\\d{3})-(\\d{6}))";
	private static PrintWriter expenseOutput = null, revenueOutput = null, diffOutput = null;

	public static void main(String[] args) {
		HashMap<String, String> parameters = new HashMap<String, String>();
		HashMap<String, Integer> fileList = new HashMap<String, Integer>();
		String budgetFile = null;
		Integer budgetYear = -1;
		HashMap<String, AccountEntry> accountEntries = new HashMap<String, AccountEntry>();
		AccountEntry topRevLevel =null, topExpLevel = null;
		
		HashMap<String, String> fundMap = new HashMap<String,String>();
		HashMap<String, String> deptMap = new HashMap<String,String>();
		HashMap<String, String> divisionMap = new HashMap<String,String>();
		HashMap<String, String> objectMap = new HashMap<String,String>();
		
		if (args.length != 1) {
			System.err.println("Directory path parameter required");
			System.exit(1);
		}
		String directoryName = args[0];
		BufferedReader input = null;
		String configFileName = directoryName + "BudgetCruncher.config";

		try {
			input = new BufferedReader(new FileReader(configFileName));
			String line;
			while ((line = input.readLine()) != null) {
				String[] nvPair = line.split(":");
				if (nvPair.length == 2) {
					String nm = nvPair[0].trim();
					String value = nvPair[1].trim();
					int commentPosition = nvPair[1].indexOf("//"); 
					if (commentPosition >= 0) {
						value = nvPair[1].substring(0, commentPosition).trim();
					}
					if (nm.equalsIgnoreCase("input1")) {
						Integer column;
						String fileName;
						nvPair = value.split(",");
						if (nvPair.length != 2) {
							System.err.println("Format error in config file line: " + line);
							System.err.println("File format is \"Filename, columnNumber\"");
							System.exit(1);
						}
						fileName = nvPair[0].trim();
						column = Integer.parseInt(nvPair[1].trim());
						fileList.put(fileName,  column);
						System.out.println("Adding file: " + fileName + " using column " + column);
					}
					else if (nm.equalsIgnoreCase("input2")) {
						nvPair = value.split(",");
						if (nvPair.length != 2) {
							System.err.println("Format error in config file line: " + line);
							System.err.println("File format is \"Filename, columnNumber\"");
							System.exit(1);
						}
						budgetFile = nvPair[0].trim();
						budgetYear = Integer.parseInt(nvPair[1].trim());
						System.out.println("Adding budget file: " + budgetFile + " using budget year " + budgetYear);
					}
					else {
						parameters.put(nm, value);
						System.out.println("Adding nm:value pair " + nm + ":" + value);
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		/*
		 * Now get the names to map account numbers to
		 */
		String filePath;
		try {
			filePath = directoryName + parameters.get("fundCodes");
			input = new BufferedReader(new FileReader(filePath));
			processCodeMap(input, fundMap);
			input.close();

			filePath = directoryName + parameters.get("deptCodes");
			input = new BufferedReader(new FileReader(filePath));
			processCodeMap(input, deptMap);
			input.close();

			filePath = directoryName + parameters.get("divisionCodes");
			input = new BufferedReader(new FileReader(filePath));
			processCodeMap(input, divisionMap);
			input.close();

			filePath = directoryName + parameters.get("objectCodes");
			input = new BufferedReader(new FileReader(filePath));
			processCodeMap(input, objectMap);
			input.close();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}

		System.out.println("");

		int baseYear = Integer.parseInt(parameters.get("startYear"));
		int numberOfYears = Integer.parseInt(parameters.get("numberOfYears"));
		boolean pruneZero = Boolean.parseBoolean(parameters.get("pruneZero"));
		System.out.println("PruneZero = " + pruneZero);
		int hierarchyCollapse = Integer.parseInt(parameters.get("hierarchyCollapse"));
		System.out.println("pruneZero = " + pruneZero + ", hiarchycollapse = " + hierarchyCollapse);

		topRevLevel = new AccountEntry(null, numberOfYears, true, 0, null, null, null, null, hierarchyCollapse);
		topExpLevel = new AccountEntry(null, numberOfYears, false, 0, null, null, null, null, hierarchyCollapse);
		try {
			Integer selectedColumn;
			/*
			 * Read in all of the files - each file specifies a name and a column to pick the value from
			 * (e.g., we may wish to use actuals from previous years, but budget from the current)
			 */
			/*
			 * Note that we are reading only years prior to last year this way. See note on budget file below
			 */
			for (String fileName : fileList.keySet()) {
				filePath = directoryName + fileName;
				selectedColumn = fileList.get(fileName);
				input = new BufferedReader(new FileReader(filePath));
				if (input != null) {
					System.out.println("Processing file " + fileName);

					processInputFile(input, accountEntries, baseYear, numberOfYears, selectedColumn,
									 fundMap, deptMap, divisionMap, objectMap, hierarchyCollapse);
					try {
						input.close();
					}
					catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
			
			/*
			 * Now read the budget file. This is where we get both the current proposed budget AND last years'
			 * original budget (if the 3rd parameter is true)
			 */
			filePath = directoryName + budgetFile;
			input = new BufferedReader(new FileReader(filePath));
			if (input != null) {
				System.out.println("Processing budget file " + budgetFile);

				processBudgetFile(input, accountEntries, true, budgetYear, baseYear, numberOfYears,
								 fundMap, deptMap, divisionMap, objectMap, hierarchyCollapse);
				try {
					input.close();
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			/*
			 * Read and add the annotations
			 */
			filePath = directoryName + "annotations.csv";
			input = new BufferedReader(new FileReader(filePath));
			if (input != null) {
				processAnnotationsFile(input, accountEntries);
				try {
					input.close();
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			ArrayList<AccountEntry> toDelete = null;
			/*
			 * Now prune entries that are zero the last 2 years
			 */
			if (pruneZero) {
				toDelete = new ArrayList<AccountEntry>();
				for (String account: accountEntries.keySet()) {
					AccountEntry a = accountEntries.get(account);
					Double[] values = a.getValues();
					if (values != null && values.length > 1) {
						double d = Math.abs(values[values.length-1]) + Math.abs(values[values.length-2]);
						if (d < 0.5) toDelete.add(a);
					}
				}
				while (toDelete.size() > 0) {
					AccountEntry a = toDelete.remove(0);
					accountEntries.remove(a.accountCode);
				}
			}
			
			/*
			 * Next, let's just aggregate any accounts that are never over a certain amount
			 */
			
			double cutoff = Double.parseDouble(parameters.get("amountCutoff"));
			System.out.println("Got a cut off of " + cutoff);
			toDelete = new ArrayList<AccountEntry>();
			for (String account: accountEntries.keySet()) {
				AccountEntry a = accountEntries.get(account);
				Double[] values = a.getValues();
				double maxValue = 0.;
				for (int i=0; i<values.length; ++i) {
					maxValue = Math.max(maxValue, Math.abs(values[i]));
				}
				if (maxValue < cutoff) {
					toDelete.add(a);
				}
			}
			while (toDelete.size() > 0) {
				AccountEntry a = toDelete.remove(0);
				accountEntries.remove(a.accountCode);
				if (a.isRevenue()) {
					String key = a.fund + "." + a.merged + ".All Other Revenues";
					AccountEntry agg = accountEntries.get(key);
					if (agg == null) {
						agg = new AccountEntry (key, numberOfYears, true, 3, a.fund, a.department, a.division, "All Other Revenues", hierarchyCollapse);
						accountEntries.put(key,  agg);
					}
					agg.addValues(a.getValues());
				}
				else {
					String key = a.fund + "." + a.merged + ".All Other Expenses";
					AccountEntry agg = accountEntries.get(key);
					if (agg == null) {
						agg = new AccountEntry (key, numberOfYears, false, 3, a.fund, a.department, a.division, "All Other Expenses", hierarchyCollapse);
						accountEntries.put(key,  agg);
					}
					agg.addValues(a.getValues());					
				}
			}
			
			/*
			 * Now let's build the hierarchy for output
			 */
			int count = 0;
			for (String account: accountEntries.keySet()) {
				++count;
				AccountEntry accountEntry = accountEntries.get(account);
				if (accountEntry.isRevenue())
					topRevLevel.addEntry(accountEntry, hierarchyCollapse);
				else
					topExpLevel.addEntry(accountEntry, hierarchyCollapse);
			}
			
			System.out.println("Count is " + count);
			
			/*  
			 * Output revenues and expenses files
			 */
			try {
				String header = "LEVEL1,LEVEL2,LEVEL3,TOOLTIP,SOURCE,SOURCE URL";
				for (int i=0; i< numberOfYears; ++i) {
					int year = baseYear + i;
					header += "," + year;
				}
				header += ",LEVEL";
				
				expenseOutput  = new PrintWriter(new BufferedWriter(new FileWriter(directoryName + "expenses.csv")));
				expenseOutput.println(header);
				topExpLevel.printEntries(expenseOutput, hierarchyCollapse);
				
				revenueOutput  = new PrintWriter(new BufferedWriter(new FileWriter(directoryName + "revenues.csv")));				
				revenueOutput.println(header);
				topRevLevel.printEntries(revenueOutput, hierarchyCollapse);
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (revenueOutput  != null) {
				revenueOutput .close();
			}
			if (expenseOutput  != null) {
				expenseOutput .close();
			}
						
			/*
			 * Now output the diffs file
			 */
			diffOutput  = new PrintWriter(new BufferedWriter(new FileWriter(directoryName + "budgetdiffs.csv")));
			 		
			try {
				diffOutput.println("Fund,Department,Division,Account,Amount,Current,Revenue,Annotation,");

				for (String account: accountEntries.keySet()) {
					AccountEntry a = accountEntries.get(account);
					String annotation = a.getAnnotation();
					if (annotation == null) annotation = " ";
					String diffLine = a.fund + "," + a.department + "," 
							+ a.division + "," + a.account;
					Double[] values = a.getValues();
					Double oldVal = 0.0, newVal = 0.0, diff = 0.0;
					if (values != null) {
						if (values[numberOfYears-2] != null) oldVal = values[numberOfYears-2];
						if (values[numberOfYears-1] != null) newVal = values[numberOfYears-1];
					}

					diff = newVal - oldVal;
					diffLine += "," + diff.toString() + "," + newVal + "," + a.isRevenue() + "," + annotation;
					diffOutput.println(diffLine);
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (diffOutput  != null) {
				diffOutput .close();
			}

		}
		catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Processing complete");
	}
	
	static void processCodeMap (BufferedReader input, HashMap<String,String> map) throws IOException {
		String line;
		String[] items;
		input.readLine(); // Skip the header
		while ((line = input.readLine()) != null) {
			items = line.split(",");
			if (items.length == 2) {
				map.put(items[0].trim(), items[1].trim());
			}
		}
	}
	
	static void processBudgetFile(BufferedReader input, HashMap<String, AccountEntry> accountEntries, 
			boolean getLastYearBudget,
			int year, int baseYear, int nYears,
			HashMap<String, String> fundMap, HashMap<String, String> deptMap, HashMap<String, 
			String> divisionMap, HashMap<String, String> objectMap, int hierarchyCollapse) throws Exception {
		String line, previousLine = null;
		String[] items;
		int yearDelta;
		String budgetRegex = "((\\d{4})-(\\d{2})-(\\d{2})-([\\d\\w]{3})-(\\d{4})-(\\d{5})-(\\d{5})-(\\d{3})-(\\d{6}))-";
		Pattern pattern = Pattern.compile(budgetRegex);
		int count = 0, currentYearBudgetColumn = 9, previousYearBudgetColumn = 5; // 5 for last year, 9 for this year budget

		yearDelta = year - baseYear;

		// Skip some initial lines
		for (int i=0; i<7; ++i) input.readLine(); 

		// Process line items
		while ((line = input.readLine()) != null) {
			items = line.split(",");
			if (items.length == 0) continue;
			String accountNum = items[0];
			Matcher matcher = pattern.matcher(accountNum);
			if (matcher.matches()) {
				items = previousLine.split(",");
				String accountCode = matcher.group(1);
				AccountEntry acctEntry = accountEntries.get(accountCode);

				if (acctEntry == null) {
					boolean isRev = false;
					String fundCode = matcher.group(2);
					String deptCode = matcher.group(4);
					String divisionCode = matcher.group(5);
					String objectCode = matcher.group(10);
					if (objectCode.charAt(0) == '4') isRev = true;

					// Get rid of leading zeros
					try {
						Integer idept = Integer.parseInt(deptCode);
						deptCode = idept.toString();
					}
					catch (Exception e) {
						// Skip it - not an integer
					}
					acctEntry = new AccountEntry (accountCode, nYears, isRev, 3,
							fundMap.get(fundCode), deptMap.get(deptCode), 
							divisionMap.get(divisionCode), objectMap.get(objectCode), hierarchyCollapse);
					accountEntries.put(accountCode, acctEntry);
				}
				acctEntry.setValue(yearDelta, items[currentYearBudgetColumn].trim(), true);
				if (getLastYearBudget) 
					acctEntry.setValue(yearDelta-1, items[previousYearBudgetColumn].trim(), true);
			}
			previousLine = line;
		}
		System.out.println("Count of matches = " + count);
	}	

	
	static void processInputFile(BufferedReader input, HashMap<String, AccountEntry> accountEntries, 
								int baseYear, int nYears, int selectedColumn,
								HashMap<String, String> fundMap, HashMap<String, String> deptMap, HashMap<String, 
								String> divisionMap, HashMap<String, String> objectMap,
								int hierarchyCollapse) throws Exception {
		String line;
		String[] items;
		int year, yearDelta;
		Pattern pattern = Pattern.compile(accountRegex);

		// Read the date range
		line = input.readLine();
		items = line.split(",");
		year = Integer.parseInt(items[4]);
		yearDelta = year - baseYear;

		// Skip column headers
		input.readLine(); 
		input.readLine(); 

		// Process line items
		while ((line = input.readLine()) != null) {
			items = line.split(",");
			if (items.length == 0) continue;
			String accountNum = items[0];
			Matcher matcher = pattern.matcher(accountNum);
			if (matcher.matches()) {
				String accountCode = matcher.group(1);
				AccountEntry acctEntry = accountEntries.get(accountCode);
				
				if (acctEntry == null) {
					boolean isRev = false;
					String fundCode = matcher.group(2);
					String deptCode = matcher.group(4);
					String divisionCode = matcher.group(5);
					String objectCode = matcher.group(10);
					if (objectCode.charAt(0) == '4') isRev = true;
					
					// Get rid of leading zeros
					try {
						Integer idept = Integer.parseInt(deptCode);
						deptCode = idept.toString();
					}
					catch (Exception e) {
						// Skip it - not an integer
					}
					acctEntry = new AccountEntry (accountCode, nYears, isRev, 3,
												  fundMap.get(fundCode), deptMap.get(deptCode), 
												  divisionMap.get(divisionCode), objectMap.get(objectCode), hierarchyCollapse);
					accountEntries.put(accountCode, acctEntry);
				}
				acctEntry.setValue(yearDelta, items[selectedColumn].trim(), true);
			}
		}
	}	
	
	static void processAnnotationsFile(BufferedReader input, HashMap<String, AccountEntry> accountEntries) throws Exception {
		String line;
		String[] items;
		Pattern pattern = Pattern.compile(annotationRegex);

		// Process line items
		while ((line = input.readLine()) != null) {
			items = line.split(",");
			if (items.length == 0) continue;
			String accountNum = items[0];
			Matcher matcher = pattern.matcher(accountNum);
			if (matcher.matches()) {
				AccountEntry acctEntry = accountEntries.get(accountNum);
				if (acctEntry == null) {
					throw new Exception("Unable to find annotation match: " + line);
				}
				acctEntry.setAnnotation(items[1].trim());
			}
		}
	}		
}
