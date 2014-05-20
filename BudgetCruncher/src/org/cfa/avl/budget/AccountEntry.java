package org.cfa.avl.budget;

public class AccountEntry {
	boolean isRevenueFlag = false;
	String accountCode = null;
	String annotation = null;
	String[] values = null;
	
	public AccountEntry(String code, int nYears, boolean isRev) {
		accountCode = code;
		values = new String[nYears];
		isRevenueFlag = isRev;
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
	
	public String[] getValues () {
		return values;
	}
	
	public String getValue(int year) {
		String val = null;
		if (values != null && values.length > year) val = values[year];
		return val;
	}
	
	public void setValue(int year, String val) {
		values[year] = val;
	}
	
	public void setAnnotation(String ann) {
		annotation = ann;
	}
}
