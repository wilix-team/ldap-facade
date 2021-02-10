package dev.wilix.ldap.facade.server.processing;

import dev.wilix.ldap.facade.server.config.properties.LdapConfigurationProperties;

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
    private final Pattern groupEntryDnPattern;

    private final Pattern searchFilterToEntryNamePattern;

    private final String userNameToDnTemplate;

    public LdapNamingHelper(LdapConfigurationProperties ldapProperties) {
        this.ldapProperties = ldapProperties;

        // FIXME Переработать!
        String mainNameAttr = ldapProperties.getMainNameAttribute();
        userEntryDnPattern = Pattern.compile(mainNameAttr + "=(.*)," + ldapProperties.getUsersBaseDn());
        serviceEntryDnPattern = Pattern.compile(mainNameAttr + "=(.*)," + ldapProperties.getServicesBaseDn());
        groupEntryDnPattern = Pattern.compile(mainNameAttr + "=(.*)," + ldapProperties.getGroupsBaseDn());

        searchFilterToEntryNamePattern = Pattern.compile("\\(" + ldapProperties.getMainNameAttribute() + "=(.+?)\\)");

        userNameToDnTemplate = mainNameAttr + "=%s," + ldapProperties.getGroupsBaseDn();
    }

    boolean isUserDn(String dn) {
        return userEntryDnPattern.matcher(dn).matches();
    }

    boolean isServiceDn(String dn) {
        return serviceEntryDnPattern.matcher(dn).matches();
    }

    boolean isGroupDn(String dn) {
        return groupEntryDnPattern.matcher(dn).matches();
    }

    String extractUserNameFromDn(String userDn) {
        return firstGroupFromPattern(userEntryDnPattern, userDn);
    }

    String extractEntryNameFromSearchFilter(String searchFilter) {
        return firstGroupFromPattern(searchFilterToEntryNamePattern, searchFilter);
    }

    String extractServiceNameFromDn(String serviceDn) {
        return firstGroupFromPattern(serviceEntryDnPattern, serviceDn);
    }

    String generateDnForUserEntry(Map<String, List<String>> userEntry) {

        // FIXME Требуются проверки на корректные значения каждого промежуточного объекта.
        String userName = userEntry.get(ldapProperties.getMainNameAttribute()).get(0);

        return String.format(userNameToDnTemplate, userName);
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
