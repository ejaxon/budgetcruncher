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
	private static PrintWriter output = null;

	public static void main(String[] args) {
		HashMap<String, String> parameters = new HashMap<String, String>();
		HashMap<String, Integer> fileList = new HashMap<String, Integer>();
		HashMap<String, AccountEntry> accountEntries = new HashMap<String, AccountEntry>();
		
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
				if (nvPair.length != 2) {
					System.err.println("Format error in config file line: " + line);
					System.err.println("Entries are name:value pairs separated by a colon (:)");
					System.err.println("Neither the name nor the value may contain a colon (:)");
					System.exit(1);
				}
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
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("");

		int baseYear = Integer.parseInt(parameters.get("startYear"));
		int numberOfYears = Integer.parseInt(parameters.get("numberOfYears"));
		
		try {
			Integer selectedColumn;
			
			/*
			 * Read in all of the files - each file specifies a name and a column to pick the value from
			 * (e.g., we may wish to use actuals from previous years, but budget from the current)
			 */
			for (String fileName : fileList.keySet()) {
				String filePath = directoryName + fileName;
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
			/*
			 * Now output the file
			 */
			String filePath = directoryName + parameters.get("outputFileName");
			output  = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
			 		
			try {
				String line = "";
				
				for (int i=0; i< numberOfYears; ++i) {
					int year = baseYear + i;
					line += ", " + year;
				}
				
				output .println("Fund, Department, Division, Account" + line);
				Pattern pattern = Pattern.compile(accountRegex);
				for (String account: accountEntries.keySet()) {
					AccountEntry accountEntry = accountEntries.get(account);
					Matcher matcher = pattern.matcher(account);
					if (matcher.matches()) {
						String fundCode = matcher.group(2);
						String deptCode = matcher.group(4);
						String divisionCode = matcher.group(5);
						String object = matcher.group(10);
						line = fundCode + ", " + deptCode + ", " + divisionCode + ", " + object;
						for (int i=0; i<numberOfYears; ++i) {
							line += ", " + accountEntry.getValue(i);
						}
						output .println(line);
					}
					else {
						throw new Exception("Failed to match account " + account);
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (output  != null) {
				output .close();
			}

		}
		catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Processing complete");
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
				AccountEntry acctEntry = accountEntries.get(accountNum);
				if (acctEntry == null) {
					acctEntry = new AccountEntry (accountNum, nYears);
					accountEntries.put(accountNum, acctEntry);
				}
				acctEntry.setValue(yearDelta, items[selectedColumn].trim());
				//System.out.println("Fund = " + matcher.group(2) + " and account code is " + matcher.group(1));
			}
		}
	}		
	
//	if (false) { // From old code
// 		String inputFileName = directoryName+"general_fund_2013.csv";
// 		String outputFileName = directoryName+"general_fund_2013_out.csv";
//
//		PrintWriter output = null;
//
//		String[] accountTags = new String[8];
//		int currentLevel = -1;
//		 		
// 		for (int i=0; i<8; ++i) accountTags[i] = " "; // Initialize the account tags
// 		
// 		try {
//			input = new BufferedReader(new FileReader(inputFileName));
//			output = new PrintWriter(new BufferedWriter(new FileWriter(outputFileName)));
//		}
//		catch (Exception ex) {
//			ex.printStackTrace();
//		}
//
//		// Read the file
//		try {
//			String line;
//			String[] items, items2;
//			int normalLength = 8;
//			// Read the date range
//			line = input.readLine();
//			System.out.println("Processing file " + inputFileName);
//			System.out.println("Date range: " + line);
//
//			// Now set up column headers
//			line = input.readLine(); 
//			items = line.split(",");
//			line = input.readLine(); 
//			items2 = line.split(",");
//			line = "Account";
//			
//			for (int ii=1; ii<8; ++ii) {
//				String nm = (items[ii] == null)?items2[ii]:items[ii]+ " " + items2[ii];
//				line += ", " + nm;
//			}
//			line += ", AccountNumber";
//			output.println("Fund, Level1, Level2, Level3, Level4, Level5, Level6, Level7, " + line);
//
//			// Process line items
//			while ((line = input.readLine()) != null) {
//				items = line.split(",");
//				if (items.length > 0) {
//					if (items.length == 2) {
//						// Push the account name on to the "stack"
//						++currentLevel;
//						accountTags[currentLevel] = items[1];
//					}
//					else if (items[0].startsWith("TOTAL") && currentLevel >= 0) {
//						// Ignore the data, but pop the "stack"
//						accountTags[currentLevel] = " ";
//						--currentLevel;
//					}
//					else if (items[0].startsWith("GRAND") || items.length < normalLength) {
//						// Skip it
//					}
//					else if (items.length == normalLength) {
//						String s = "";
//						for (int ii=0; ii<8; ++ii) {
//							s += accountTags[ii] + ", ";
//						}
//						int nameStart = items[0].indexOf(' ');
//						String accountName = items[0].substring(nameStart+1);
//						String accountNumber = items[0].substring(0, nameStart);
//						s += accountName + ", ";
//						for (int ii=1; ii<normalLength; ++ii) s += items[ii] + ", ";
//						s += accountNumber;
//						output.println(s);
//					}
//					else {
//						throw new Exception("Yikes! Unknown line type with length " + items.length + "  " + line);
//					}
//				}
//			}
//		
//			output.flush();
//			output.close();			
//		}
//		catch (Exception ex) {
//			ex.printStackTrace();
//		}
//		if (input != null) {
//			try {
//				input.close();
//			}
//			catch (Exception ex) {
//				ex.printStackTrace();
//			}
//		}
//	}

}
