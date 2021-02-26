package dev.wilix.ldap.facade.wilix.crm.facade.processing;

import dev.wilix.ldap.facade.wilix.crm.facade.config.properties.LdapConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общие утилиты для работы с путями ldap.
 */
public class LdapNamingHelper {

    private final LdapConfigurationProperties ldapProperties;

    private final Pattern userEntryDnPattern;
    private final Pattern serviceEntryDnPattern;

    private final String userNameToDnTemplate;
    private final String groupNameToDnTemplate;

    public LdapNamingHelper(LdapConfigurationProperties ldapProperties) {
        this.ldapProperties = ldapProperties;

        // FIXME Переработать!
        String mainNameAttr = ldapProperties.getMainNameAttribute();
        userEntryDnPattern = Pattern.compile(mainNameAttr + "=(.*)," + ldapProperties.getUsersBaseDn());
        serviceEntryDnPattern = Pattern.compile(mainNameAttr + "=(.*)," + ldapProperties.getServicesBaseDn());

        userNameToDnTemplate = mainNameAttr + "=%s," + ldapProperties.getUsersBaseDn();
        groupNameToDnTemplate = mainNameAttr + "=%s," + ldapProperties.getGroupsBaseDn();
    }

    boolean isUserDn(String dn) {
        return userEntryDnPattern.matcher(dn).matches();
    }

    boolean isServiceDn(String dn) {
        return serviceEntryDnPattern.matcher(dn).matches();
    }

    String getUserClassName() {
        return ldapProperties.getUserClassName();
    }

    String getGroupClassName() {
        return ldapProperties.getGroupClassName();
    }

    String extractUserNameFromDn(String userDn) {
        return firstGroupFromPattern(userEntryDnPattern, userDn);
    }

    String extractServiceNameFromDn(String serviceDn) {
        return firstGroupFromPattern(serviceEntryDnPattern, serviceDn);
    }

    String generateDnForUserEntry(Map<String, List<String>> userEntry) {

        // FIXME Требуются проверки на корректные значения каждого промежуточного объекта.
        String userName = userEntry.get(ldapProperties.getMainNameAttribute()).get(0);

        return String.format(userNameToDnTemplate, userName);
    }

    String generateDnForUserEntryFromAttribute(String entryName) {
        return String.format(userNameToDnTemplate, entryName);
    }

    String generateDnForGroupEntry(Map<String, List<String>> groupEntry) {

        // FIXME Требуются проверки на корректные значения каждого промежуточного объекта.
        String userName = groupEntry.get(ldapProperties.getMainNameAttribute()).get(0);

        return String.format(groupNameToDnTemplate, userName);
    }

    String generateDnForGroupEntryFromAttribute(String entryName) {
        return String.format(groupNameToDnTemplate, entryName);
    }

    private static String firstGroupFromPattern(Pattern regExpPattern, String valueToScan) {
        String result = null;

        Matcher matcher = regExpPattern.matcher(valueToScan);
        if (matcher.find()) {
            result = matcher.group(1);
        }

        return result;
    }

}
