package org.cfa.avl.budget;

public class AccountEntry {
	String accountCode = null;
	String annotation = null;
	String[] values = null;
	
	public AccountEntry(String code, int nYears) {
		accountCode = code;
		values = new String[nYears];
	}

	public String getAccountCode () {
		return accountCode;
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
}
