package com.example.google_contacts_app.model;

import java.util.List;

public class Contact {
    private String resourceName; // Add this field
    private String name;
    private List<String> emailAddresses;
    private List<String> phoneNumbers;

    public Contact() {}

    public Contact(String name, List<String> emailAddresses, List<String> phoneNumbers) {
        this.name = name;
        this.emailAddresses = emailAddresses;
        this.phoneNumbers = phoneNumbers;
    }

    public Contact(String resourceName, String name, List<String> emailAddresses, List<String> phoneNumbers) {
        this.resourceName = resourceName;
        this.name = name;
        this.emailAddresses = emailAddresses;
        this.phoneNumbers = phoneNumbers;
    }

    // Getters and Setters
    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getEmailAddresses() {
        return emailAddresses;
    }

    public void setEmailAddresses(List<String> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }

    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<String> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    @Override
    public String toString() {
        return "Contact{" +
                "resourceName='" + resourceName + '\'' +
                ", name='" + name + '\'' +
                ", emailAddresses=" + emailAddresses +
                ", phoneNumbers=" + phoneNumbers +
                '}';
    }
}