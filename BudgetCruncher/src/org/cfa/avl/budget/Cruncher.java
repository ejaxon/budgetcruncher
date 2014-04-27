package org.cfa.avl.budget;

import java.io.*;

public class Cruncher {

	public static void main(String[] args) {
		final int normalLength = 8;
		if (args.length != 1) {
			System.err.println("Directory path parameter required");
			System.exit(1);
		}
		String directoryName = args[0];
 		String inputFileName = directoryName+"general_fund_2013.csv";
 		String outputFileName = directoryName+"general_fund_2013_out.csv";

		BufferedReader input = null;
		PrintWriter output = null;

		String[] accountTags = new String[8];
		int currentLevel = -1;
		 		
 		for (int i=0; i<8; ++i) accountTags[i] = " "; // Initialize the account tags
 		
 		try {
			input = new BufferedReader(new FileReader(inputFileName));
			output = new PrintWriter(new BufferedWriter(new FileWriter(outputFileName)));
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}

		// Read the file
		try {
			String line;
			String[] items, items2;
			// Read the date range
			line = input.readLine();
			System.out.println("Processing file " + inputFileName);
			System.out.println("Date range: " + line);

			// Now set up column headers
			line = input.readLine(); 
			items = line.split(",");
			line = input.readLine(); 
			items2 = line.split(",");
			line = "Account";
			
			for (int ii=1; ii<8; ++ii) {
				String nm = (items[ii] == null)?items2[ii]:items[ii]+ " " + items2[ii];
				line += ", " + nm;
			}
			line += ", AccountNumber";
			output.println("Fund, Level1, Level2, Level3, Level4, Level5, Level6, Level7, " + line);

			// Process line items
			while ((line = input.readLine()) != null) {
				items = line.split(",");
				if (items.length > 0) {
					if (items.length == 2) {
						// Push the account name on to the "stack"
						++currentLevel;
						accountTags[currentLevel] = items[1];
					}
					else if (items[0].startsWith("TOTAL") && currentLevel >= 0) {
						// Ignore the data, but pop the "stack"
						accountTags[currentLevel] = " ";
						--currentLevel;
					}
					else if (items[0].startsWith("GRAND") || items.length < normalLength) {
						// Skip it
					}
					else if (items.length == normalLength) {
						String s = "";
						for (int ii=0; ii<8; ++ii) {
							s += accountTags[ii] + ", ";
						}
						int nameStart = items[0].indexOf(' ');
						String accountName = items[0].substring(nameStart+1);
						String accountNumber = items[0].substring(0, nameStart);
						s += accountName + ", ";
						for (int ii=1; ii<normalLength; ++ii) s += items[ii] + ", ";
						s += accountNumber;
						output.println(s);
					}
					else {
						throw new Exception("Yikes! Unknown line type with length " + items.length + "  " + line);
					}
				}
			}
		
			output.flush();
			output.close();			
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		if (input != null) {
			try {
				input.close();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		System.out.println("Processing complete");
	}
}
