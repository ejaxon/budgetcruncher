package org.cfa.avl.budget;

import java.io.PrintWriter;
import java.util.HashMap;

public class AccountEntry {
	int level = -1;
	String fund = null;
	String department = null;
	String division = null;
	String merged = null;
	String account = null;
	boolean revenueFlag = false;
	String accountCode = null;
	String annotation = null;
	Double[] values = null;
	HashMap<String,AccountEntry> children = null;
	
	
	public AccountEntry(String code, int nYears, boolean isRev, int lev, String pFund, String pDept, String pDiv, String pAccount, 
			int hierarchyCollapse) {
		accountCode = code;
		values = new Double[nYears];
		for (int i=0; i<nYears; ++i) values[i] = 0.0;
		revenueFlag = isRev;
		level = lev;
		fund = pFund;
		department =pDept;
		division = pDiv;
		account = pAccount;
		if (hierarchyCollapse == 0) { // Merge department & division
			if (department != null && division != null) {
				merged = pDept + "/" + pDiv;
			}
		}
		else if (hierarchyCollapse == 1) { // Merge division & account
			if (division != null && account != null) {
				merged = pDiv + "/" + pAccount;
			}
		}
		else { // just aggregate accounts up to division level
			merged = pDiv;
		}
	}

	public void printEntries (PrintWriter writer, int hierarchyCollapse) throws Exception {
		String line = "";
		if (children == null || level == 3) { // Normal output line
			System.out.println("Level " + level + ": " + division);

			if (annotation == null) annotation = "";
			if (hierarchyCollapse == 0) {
				line = fund + "," + merged + "," + account + "," + annotation + ",,";
			}
			else if (hierarchyCollapse == 2){
				line = fund + "," + department + "," + division + "," + annotation + ",,";
			}
			else {
				System.out.println("Not completed in AccountEntry.addEntry for hierarchyCollapse=1");
				System.exit(1);
			}
			/*
			 * Now deal with the budget/actual history
			 */
			for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
			writer.println(line + ",3");
		}
		else {
			if (hierarchyCollapse ==0 || level < 3) {
				for (String account: children.keySet()) {
					AccountEntry accountEntry = children.get(account);
					accountEntry.printEntries(writer, hierarchyCollapse);
				}
			}
			if (level == 0) {
				line = "REVENUES TOTAL,,,,,";
				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",0");				
			}
			else if (level == 1) {
				System.out.println("Level " + level + ": " + fund);

				line = fund + " Total,,,,,";
				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",1");				
			}
			else if (level == 2) {
				System.out.println("Level " + level + ": " + department);

				if (hierarchyCollapse == 0)
					line = fund + "," + merged + " Total,,,,";
				else 
					line = fund + "," + department + " Total,,,,";

				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",2");				
			}
		}
	}
	
	public void addEntry (AccountEntry a, int hierarchyCollapse) {
		if (children == null && level < 3) children = new HashMap<String,AccountEntry>();
		this.addValues(a.getValues()); // Add here
		if (level == 0) { // use Fund
			AccountEntry next = children.get(a.fund);
			if (next == null) {
				next = new AccountEntry (null, values.length, false, level+1, a.fund, null, null, null, hierarchyCollapse);
				children.put(a.fund, next);
			}
			next.addEntry(a, hierarchyCollapse);
		}
		else if (level == 1) {
			AccountEntry next = null;
			if (hierarchyCollapse == 0) { // use the merged member DeptDiv
				next = children.get(a.merged);
				if (next == null) {
					next = new AccountEntry (null, values.length, false, level+1, a.fund, a.department, a.division, null, hierarchyCollapse);
					children.put(a.merged, next);
				}
			}
			else { // use Department
				next = children.get(a.department);
				if (next == null) {
					next = new AccountEntry (null, values.length, false, level+1, a.fund, a.department, a.division, null, hierarchyCollapse);
					children.put(a.department, next);
				}
			}
			next.addEntry(a, hierarchyCollapse);
		}
		else if (level == 2) {
			AccountEntry next = null;
			if (hierarchyCollapse == 0) { //Just add as a child by account, bottom of the hierarchy
				children.put(a.account, a);
			}
			else if (hierarchyCollapse == 2) { // Just add as a child by merged (which is either division or division+account), but call again to add numbers
				next = children.get(a.division);
				if (next == null) {
					next = new AccountEntry (null, values.length, false, level+1, a.fund, a.department, a.division, null, hierarchyCollapse);
					children.put(a.division, next);
				}	
				next.addEntry(a, hierarchyCollapse); // just to add the numbers in.
			}
			else {
				System.out.println("Not completed in AccountEntry.addEntry for hierarchyCollapse=1");
				System.exit(1);
			}
		}
		// Nothing for level == 3, since it's just about adding
	}

	public boolean isRevenue() {
		return revenueFlag;
	}
	public String getAccountCode () {
		return accountCode;
	}
	
	public String getAnnotation() {
		return annotation;
	}
	
	public Double[] getValues () {
		return values;
	}
	
	public Double getValue(int year) {
		Double val = null;
		if (values != null && values.length > year) val = values[year];
		return val;
	}
	
	public void setValue(int year, String val, Boolean rescale) {
		values[year] = Double.parseDouble(val);
		if (rescale && revenueFlag) values[year] *= -1.;
	}
	public void addValues (Double[] vals) {
		for (int i=0; i<values.length; ++i) {
			values[i] += vals[i];
		}
	}
	public void scaleValues(Double factor) {
		for (int i=0; i<values.length; ++i) {
			values[i] *= factor;
		}
	}
	public void setAnnotation(String ann) {
		annotation = ann;
	}
}
