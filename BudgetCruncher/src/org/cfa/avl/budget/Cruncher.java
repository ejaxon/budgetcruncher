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
		HashMap<String, AccountEntry> accountEntries = new HashMap<String, AccountEntry>();
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
		
		System.out.println("");

		int baseYear = Integer.parseInt(parameters.get("startYear"));
		int numberOfYears = Integer.parseInt(parameters.get("numberOfYears"));
		
		try {
			Integer selectedColumn;
			String filePath;
			/*
			 * Read in all of the files - each file specifies a name and a column to pick the value from
			 * (e.g., we may wish to use actuals from previous years, but budget from the current)
			 */
			for (String fileName : fileList.keySet()) {
				filePath = directoryName + fileName;
				selectedColumn = fileList.get(fileName);
				input = new BufferedReader(new FileReader(filePath));
				if (input != null) {
					System.out.println("Processing file " + fileName);
					processInputFile(input, accountEntries, baseYear, numberOfYears, selectedColumn);
					try {
						input.close();
					}
					catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
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

			/*
			 * Now output the file
			 */
			filePath = directoryName + "expenses.csv";
			expenseOutput  = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
			filePath = directoryName + "revenues.csv";
			revenueOutput  = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
			filePath = directoryName + "budgetdiffs.csv";
			diffOutput  = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
			 		
			try {
				String line = "";
				
				for (int i=0; i< numberOfYears; ++i) {
					int year = baseYear + i;
					line += ", " + year;
				}
				
				expenseOutput.println("Fund, Department, Division, Account" + line);
				revenueOutput.println("Fund, Department, Division, Account" + line);
				diffOutput.println("Fund,Department,Division,Account,Amount,Annotation,");

				Pattern pattern = Pattern.compile(annotationRegex);
				for (String account: accountEntries.keySet()) {
					AccountEntry accountEntry = accountEntries.get(account);
					Matcher matcher = pattern.matcher(account);
					if (matcher.matches()) {
						String fundCode = matcher.group(2);
						String deptCode = matcher.group(4);
						String divisionCode = matcher.group(5);
						String object = matcher.group(10);
						String annotation = accountEntry.getAnnotation();
						if (annotation == null) annotation = " ";
						line = fundMap.get(fundCode) + "," + deptMap.get(deptCode) + "," 
								+ divisionMap.get(divisionCode) + "," + objectMap.get(object);
						/*
						 * Deal with the budget difference
						 */
						String[] values = accountEntry.getValues();
						Double oldVal = 0.0, newVal = 0.0, diff = 0.0;
						if (values != null) {
							if (values[numberOfYears-2] != null) oldVal = Double.parseDouble(values[numberOfYears-2]);
							if (values[numberOfYears-1] != null) newVal = Double.parseDouble(values[numberOfYears-1]);
						}
						diff = newVal - oldVal;
						String diffLine = line + "," + diff.toString() + "," + annotation;
						diffOutput.println(diffLine);
						/*
						 * Now deal with the budget/actual history
						 */
						for (int i=0; i<numberOfYears; ++i) {
							line += "," + accountEntry.getValue(i);
						}
						line += "," + annotation;
						@SuppressWarnings("resource")
						PrintWriter writer = (accountEntry.isRevenue())?revenueOutput:expenseOutput;

						writer.println(line);
					}
					else {
						throw new Exception("Failed to match account " + account);
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (expenseOutput  != null) {
				expenseOutput .close();
			}
			if (revenueOutput  != null) {
				revenueOutput .close();
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
	
	static void processInputFile(BufferedReader input, HashMap<String, AccountEntry> accountEntries, 
							int baseYear, int nYears, int selectedColumn) throws Exception {
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
					String objectCode = matcher.group(10);
					if (objectCode.charAt(0) == '4') isRev = true;
					acctEntry = new AccountEntry (accountCode, nYears, isRev);
					accountEntries.put(accountCode, acctEntry);
				}
				acctEntry.setValue(yearDelta, items[selectedColumn].trim());
				//System.out.println("Fund = " + matcher.group(2) + " and account code is " + matcher.group(1));
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
