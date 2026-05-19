package org.sakaiproject.reservation;

import java.util.ArrayList;
import java.util.List;

public class CustomField {
    private String id;
    private String label;
    private String type;        // text, textarea, select, date
    private boolean required;
    private List<String> options; // only for type=select

    public CustomField() {
        this.options = new ArrayList<>();
    }

    public CustomField(String id, String label, String type, boolean required, List<String> options) {
        this.id       = id;
        this.label    = label;
        this.type     = type;
        this.required = required;
        this.options  = options != null ? options : new ArrayList<>();
    }

    public String getId()                  { return id; }
    public void setId(String id)           { this.id = id; }
    public String getLabel()               { return label; }
    public void setLabel(String label)     { this.label = label; }
    public String getType()                { return type; }
    public void setType(String type)       { this.type = type; }
    public boolean isRequired()            { return required; }
    public void setRequired(boolean r)     { this.required = r; }
    public List<String> getOptions()       { return options; }
    public void setOptions(List<String> o) { this.options = o; }
}