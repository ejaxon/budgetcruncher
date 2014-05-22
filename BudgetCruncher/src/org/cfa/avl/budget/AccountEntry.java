package org.cfa.avl.budget;

import java.io.PrintWriter;
import java.util.HashMap;

public class AccountEntry {
	int level = -1;
	String fund = null;
	String department = null;
	String division = null;
	String deptdiv = null;
	String account = null;
	boolean isRevenueFlag = false;
	String accountCode = null;
	String annotation = null;
	Double[] values = null;
	HashMap<String,AccountEntry> children = null;
	
	
	public AccountEntry(String code, int nYears, boolean isRev, int lev, String pFund, String pDept, String pDiv, String pAccount) {
		accountCode = code;
		values = new Double[nYears];
		for (int i=0; i<nYears; ++i) values[i] = 0.0;
		isRevenueFlag = isRev;
		level = lev;
		fund = pFund;
		department =pDept;
		division = pDiv;
		account = pAccount;
		if (department != null && division != null) {
			deptdiv = pDept + "/" + pDiv;
		}
	}

	public void printEntries (PrintWriter writer) throws Exception {
		String line = "";
		if (children == null) { // Normal output line
			if (annotation == null) annotation = "";
			line = fund + "," + deptdiv + "," + account + "," + annotation + ",,";
			/*
			 * Now deal with the budget/actual history
			 */
			for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
			writer.println(line + ",3");
		}
		else {
			for (String account: children.keySet()) {
				AccountEntry accountEntry = children.get(account);
				accountEntry.printEntries(writer);
			}
			if (level == 0) {
				line = "REVENUES TOTAL,,,,,";
				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",0");				
			}
			else if (level == 1) {
				line = fund + " Total,,,,,";
				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",1");				
			}
			else if (level == 2) {
				line = fund + "," + deptdiv + " Total,,,,";
				for (int i=0; i<values.length; ++i) line += "," + this.getValue(i);
				writer.println(line + ",2");				
			}
		}
	}
	
	public void addEntry (AccountEntry a) {
		if (children == null) children = new HashMap<String,AccountEntry>();
		this.addValues(a.getValues()); // Add here
		if (level == 0) { // use Fund
			AccountEntry next = children.get(a.fund);
			if (next == null) {
				next = new AccountEntry (null, values.length, false, level+1, a.fund, null, null, null);
				children.put(a.fund, next);
			}
			next.addEntry(a);
		}
		else if (level == 1) { // use DeptDiv
			AccountEntry next = children.get(a.deptdiv);
			if (next == null) {
				next = new AccountEntry (null, values.length, false, level+1, a.fund, a.department, a.division, null);
				children.put(a.deptdiv, next);
			}
			next.addEntry(a);
		}
		else { //Just add as a child, bottom of the hierarchy
			children.put(a.accountCode, a);
		}
	}

	public boolean isRevenue() {
		return isRevenueFlag;
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
		if (rescale && isRevenueFlag) values[year] *= -1.;
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
