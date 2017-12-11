package rocks.nt.project.financials.data;

public class MonthReportDataPoint {
	private String yearMonth;
	private double expenses;
	private double costs;
	private double revenue;
	private double profit;
	private double utilization;
	private double retrunOnSales;
	private Type type;

	public String getYearMonth() {
		return yearMonth;
	}

	public void setYearMonth(String yearMonth) {
		this.yearMonth = yearMonth;
	}

	public double getExpenses() {
		return expenses;
	}

	public void setExpenses(double expenses) {
		this.expenses = expenses;
	}

	public double getCosts() {
		return costs;
	}

	public void setCosts(double costs) {
		this.costs = costs;
	}

	public double getRevenue() {
		return revenue;
	}

	public void setRevenue(double revenue) {
		this.revenue = revenue;
	}

	public double getProfit() {
		return profit;
	}

	public void setProfit(double profit) {
		this.profit = profit;
	}

	public double getUtilization() {
		return utilization;
	}

	public void setUtilization(double utilization) {
		this.utilization = utilization;
	}

	public double getRetrunOnSales() {
		return retrunOnSales;
	}

	public void setRetrunOnSales(double retrunOnSales) {
		this.retrunOnSales = retrunOnSales;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public static MonthReportDataPoint getDefault(String yearMonth) {
		MonthReportDataPoint dataPoint = new MonthReportDataPoint();
		dataPoint.yearMonth = yearMonth;
		dataPoint.expenses = 0.0;
		dataPoint.costs = 0.0;
		dataPoint.revenue = 0.0;
		dataPoint.profit = 0.0;
		dataPoint.utilization = 0.0;
		dataPoint.retrunOnSales = 0.0;
		return dataPoint;
	}

	public static enum Type {
		ESTIMATED, ACTUAL;

		@Override
		public String toString() {
			if (this.equals(ACTUAL)) {
				return "act_";
			} else {
				return "est_";
			}
		}
	}
}
