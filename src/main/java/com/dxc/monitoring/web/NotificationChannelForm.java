package com.dxc.monitoring.web;

public class NotificationChannelForm {
    private String name;
    private String channelType = "EMAIL";
    private String smtpHost;
    private int smtpPort = 587;
    private String smtpFrom;
    private String smtpUsername;
    private boolean smtpAuth = true;
    private boolean smtpStartTls = true;
    private String whatsappPhoneNumberId;
    private String whatsappApiVersion = "v23.0";
    private String whatsappTemplateName;
    private String whatsappLanguageCode = "en_US";
    private String secret;
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getChannelType(){return channelType;} public void setChannelType(String v){channelType=v;}
    public String getSmtpHost(){return smtpHost;} public void setSmtpHost(String v){smtpHost=v;}
    public int getSmtpPort(){return smtpPort;} public void setSmtpPort(int v){smtpPort=v;}
    public String getSmtpFrom(){return smtpFrom;} public void setSmtpFrom(String v){smtpFrom=v;}
    public String getSmtpUsername(){return smtpUsername;} public void setSmtpUsername(String v){smtpUsername=v;}
    public boolean isSmtpAuth(){return smtpAuth;} public void setSmtpAuth(boolean v){smtpAuth=v;}
    public boolean isSmtpStartTls(){return smtpStartTls;} public void setSmtpStartTls(boolean v){smtpStartTls=v;}
    public String getWhatsappPhoneNumberId(){return whatsappPhoneNumberId;} public void setWhatsappPhoneNumberId(String v){whatsappPhoneNumberId=v;}
    public String getWhatsappApiVersion(){return whatsappApiVersion;} public void setWhatsappApiVersion(String v){whatsappApiVersion=v;}
    public String getWhatsappTemplateName(){return whatsappTemplateName;} public void setWhatsappTemplateName(String v){whatsappTemplateName=v;}
    public String getWhatsappLanguageCode(){return whatsappLanguageCode;} public void setWhatsappLanguageCode(String v){whatsappLanguageCode=v;}
    public String getSecret(){return secret;} public void setSecret(String v){secret=v;}
}
