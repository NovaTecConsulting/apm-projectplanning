package rocks.nt.project.financials.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Project assignment data object.
 * @author Alexander Wert
 *
 */
public class ProjectAssignment {

	/**
	 * Employee.
	 */
	private String employee;
	
	/**
	 * Project name.
	 */
	private String project;
	
	/**
	 * Booking status.
	 */
	private String status;
	
	/**
	 * Daily rate.
	 */
	private Double rate = null;
	
	/**
	 * Expected daily expenses.
	 */
	private Double expenses = null;
	
	/**
	 * From date.
	 */
	private LocalDate from;
	
	/**
	 * To date.
	 */
	private LocalDate to;
	
	/**
	 * Days of week to be selected.
	 */
	private Set<DayOfWeek> daysOfWeek;
	
	/**
	 * Skip public holidays.
	 */
	private boolean skipHolidays;
	
	/**
	 * Skip all non-project events.
	 */
	private boolean skipEvents;
	
	/**
	 * Color string in hex encoding.
	 */
	private String color;
	
	/**
	 * Notes string.
	 */
	private String notes;

	/**
	 * Use Builder for construction.
	 */
	private ProjectAssignment() {

	}

	/**
	 * @return the employee
	 */
	public String getEmployee() {
		return employee;
	}

	/**
	 * @return the project
	 */
	public String getProject() {
		return project;
	}

	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @return the rate
	 */
	public Double getRate() {
		return rate;
	}

	/**
	 * @return the expenses
	 */
	public Double getExpenses() {
		return expenses;
	}

	/**
	 * @return the from
	 */
	public LocalDate getFrom() {
		return from;
	}

	/**
	 * @return the to
	 */
	public LocalDate getTo() {
		return to;
	}

	/**
	 * @return the daysOfWeek
	 */
	public Set<DayOfWeek> getDaysOfWeek() {
		return daysOfWeek;
	}

	/**
	 * @return the skipHolidays
	 */
	public boolean isSkipHolidays() {
		return skipHolidays;
	}

	/**
	 * @return the color
	 */
	public String getColor() {
		return color;
	}

	/**
	 * @return the notes
	 */
	public String getNotes() {
		return notes;
	}

	/**
	 * @param employee
	 *            the employee to set
	 */
	public void setEmployee(String employee) {
		this.employee = employee;
	}
	
	/**
	 * @return the skipEvents
	 */
	public boolean isSkipEvents() {
		return skipEvents;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((daysOfWeek == null) ? 0 : daysOfWeek.hashCode());
		result = prime * result + ((employee == null) ? 0 : employee.hashCode());
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + (skipHolidays ? 1231 : 1237);
		result = prime * result + ((to == null) ? 0 : to.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProjectAssignment other = (ProjectAssignment) obj;
		if (daysOfWeek == null) {
			if (other.daysOfWeek != null)
				return false;
		} else if (!daysOfWeek.equals(other.daysOfWeek))
			return false;
		if (employee == null) {
			if (other.employee != null)
				return false;
		} else if (!employee.equals(other.employee))
			return false;
		if (from == null) {
			if (other.from != null)
				return false;
		} else if (!from.equals(other.from))
			return false;
		if (skipHolidays != other.skipHolidays)
			return false;
		if (to == null) {
			if (other.to != null)
				return false;
		} else if (!to.equals(other.to))
			return false;
		return true;
	}

	public static class ProjectAssignmentBuilder {

		private final ProjectAssignment projectAssignment;

		public ProjectAssignmentBuilder() {
			projectAssignment = new ProjectAssignment();
		}

		/**
		 * @param employee
		 *            the employee to set
		 */
		public ProjectAssignmentBuilder employee(String employee) {
			projectAssignment.employee = employee;
			return this;
		}

		/**
		 * @param color
		 *            the color to set
		 */
		public ProjectAssignmentBuilder color(String color) {
			projectAssignment.color = color;
			return this;
		}

		/**
		 * @param skipHolidays
		 *            the skipHolidays to set
		 */
		public ProjectAssignmentBuilder skipHolidays(boolean skipHolidays) {
			projectAssignment.skipHolidays = skipHolidays;
			return this;
		}	
		
		
		/**
		 * @param skipEvents the skipEvents to set
		 */
		public ProjectAssignmentBuilder skipEvents(boolean skipEvents) {
			projectAssignment.skipEvents = skipEvents;
			return this;
		}

		/**
		 * @param daysOfWeek
		 *            the daysOfWeek to set
		 */
		public ProjectAssignmentBuilder daysOfWeek(Set<DayOfWeek> daysOfWeek) {
			projectAssignment.daysOfWeek = daysOfWeek;
			return this;
		}

		/**
		 * @param to
		 *            the to to set
		 */
		public ProjectAssignmentBuilder to(LocalDate to) {
			projectAssignment.to = to;
			return this;
		}

		/**
		 * @param from
		 *            the from to set
		 */
		public ProjectAssignmentBuilder from(LocalDate from) {
			projectAssignment.from = from;
			return this;
		}

		/**
		 * @param expenses
		 *            the expenses to set
		 */
		public ProjectAssignmentBuilder expenses(double expenses) {
			projectAssignment.expenses = expenses;
			return this;
		}

		/**
		 * @param rate
		 *            the rate to set
		 */
		public ProjectAssignmentBuilder rate(double rate) {
			projectAssignment.rate = rate;
			return this;
		}

		/**
		 * @param status
		 *            the status to set
		 */
		public ProjectAssignmentBuilder status(String status) {
			projectAssignment.status = status;
			return this;
		}

		/**
		 * @param project
		 *            the project to set
		 */
		public ProjectAssignmentBuilder project(String project) {
			projectAssignment.project = project;
			return this;
		}
		
		/**
		 * @param notes the notes to set
		 */
		public ProjectAssignmentBuilder notes(String notes) {
			projectAssignment.notes = notes;
			return this;
		}
		
		/**
		 * @param notes the notes to set
		 */
		public ProjectAssignmentBuilder copy(ProjectAssignment projectAssignment) {
			this.employee(projectAssignment.getEmployee());
			this.color(projectAssignment.getColor());
			this.skipHolidays(projectAssignment.isSkipHolidays());
			this.skipEvents(projectAssignment.isSkipEvents());
			this.daysOfWeek(projectAssignment.getDaysOfWeek());
			this.to(projectAssignment.getTo());
			this.from(projectAssignment.getFrom());
			this.expenses(projectAssignment.getExpenses());
			this.rate(projectAssignment.getRate());
			this.status(projectAssignment.getStatus());
			this.project(projectAssignment.getProject());
			this.notes(projectAssignment.getNotes());
			return this;
		}

		public ProjectAssignment build() {
			return projectAssignment;
		}

	}

	public static class EventBuilder {

		private final ProjectAssignment projectAssignment;

		public EventBuilder() {
			projectAssignment = new ProjectAssignment();
			projectAssignment.status = "";
			projectAssignment.rate = 0.0;
			projectAssignment.expenses = 0.0;
			projectAssignment.daysOfWeek = new HashSet<DayOfWeek>(Arrays.asList(DayOfWeek.values()));
			projectAssignment.skipHolidays = false;
			projectAssignment.skipEvents = false;
		}

		/**
		 * @param employee
		 *            the employee to set
		 */
		public EventBuilder employee(String employee) {
			projectAssignment.employee = employee;
			return this;
		}

		/**
		 * @param color
		 *            the color to set
		 */
		public EventBuilder color(String color) {
			projectAssignment.color = color;
			return this;
		}

		/**
		 * @param to
		 *            the to to set
		 */
		public EventBuilder to(LocalDate to) {
			projectAssignment.to = to;
			return this;
		}

		/**
		 * @param from
		 *            the from to set
		 */
		public EventBuilder from(LocalDate from) {
			projectAssignment.from = from;
			return this;
		}

		/**
		 * @param event
		 *            the event to set
		 */
		public EventBuilder event(String event) {
			projectAssignment.project = event;
			return this;
		}
		
		/**
		 * @param notes the notes to set
		 */
		public EventBuilder notes(String notes) {
			projectAssignment.notes = notes;
			return this;
		}

		public ProjectAssignment build() {
			return projectAssignment;
		}

	}

}
