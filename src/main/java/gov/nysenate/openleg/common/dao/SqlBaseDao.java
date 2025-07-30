package gov.nysenate.openleg.common.dao;

import com.google.common.collect.Range;
import gov.nysenate.openleg.common.util.DateUtils;
import gov.nysenate.openleg.config.EnvironmentUtils;
import gov.nysenate.openleg.config.OpenLegEnvironment;
import gov.nysenate.openleg.legislation.BaseLegislativeContent;
import gov.nysenate.openleg.legislation.SessionYear;
import gov.nysenate.openleg.processors.bill.LegDataFragment;
import gov.nysenate.openleg.updates.UpdateType;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static gov.nysenate.openleg.common.util.DateUtils.toDate;

/**
 * Base class for SQL data access layer classes to inherit common functionality from.
 */
public abstract class SqlBaseDao {
    /** JdbcTemplate reference for use by sub classes to execute SQL queries */
    @Autowired protected JdbcTemplate jdbc;

    /** Similar to JdbcTemplate but forces the use of named query parameter for readability. */
    @Autowired protected NamedParameterJdbcTemplate jdbcNamed;

    /** Reference to the environment in which the data is stored */
    @Autowired protected OpenLegEnvironment environment;

    @Autowired protected EnvironmentUtils envUtils;

    /** --- Common Param Methods --- */

    /**
     * Returns the schema of the environment instance.
     */
    protected String schema() {
        if (environment == null) {
            throw new IllegalStateException("The environment has not been initialized. Cannot perform SQL queries " +
                    "since we can't determine which database schema to operate on.");
        }
        return environment.getSchema();
    }

    /**
     * Applies the 'last LegDataFragment id' column value. Useful for tracking which sobiFragment
     * serves as the source data for the update.
     */
    protected static void addLastFragmentParam(LegDataFragment fragment, MapSqlParameterSource params) {
        params.addValue("lastFragmentId", (fragment != null) ? fragment.getFragmentId() : null);
    }

    /**
     * Adds parameters for a date time range
     */
    protected static void addDateTimeRangeParams(MapSqlParameterSource params, Range<LocalDateTime> dateTimeRange) {
        params.addValue("startDateTime", DateUtils.toDate(DateUtils.startOfDateTimeRange(dateTimeRange)));
        params.addValue("endDateTime", DateUtils.toDate(DateUtils.endOfDateTimeRange(dateTimeRange)));
    }

    /**
     * Gets a new parameter map containing params for the given date time range
     */
    protected static MapSqlParameterSource getDateTimeRangeParams(Range<LocalDateTime> dateTimeRange) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        addDateTimeRangeParams(params, dateTimeRange);
        return params;
    }

    /**
     * Applies the published date / modified date column values.
     */
    protected static void addModPubDateParams(LocalDateTime modifiedDate, LocalDateTime publishedDate, MapSqlParameterSource params) {
        params.addValue("modifiedDateTime", toDate(modifiedDate));
        params.addValue("publishedDateTime", toDate(publishedDate));
    }

    /**
     * Convenience method for setting the modified and published date time via the default columns
     * in the result set. Use this method only when the result set is guaranteed to have these
     * default columns.
     */
    protected static void setModPubDatesFromResultSet(BaseLegislativeContent obj, ResultSet rs) throws SQLException {
        obj.setModifiedDateTime(getLocalDateTimeFromRs(rs, "modified_date_time"));
        obj.setPublishedDateTime(DateUtils.getLocalDateTime(rs.getTimestamp("published_date_time")));
    }

    /**
     * Returns a new string where the substitution key 'e.g. ${insertWhereClause}' is replaced with the
     * given replacement string.
     */
    protected static String queryReplace(String originalQuery, String key, String replacement) {
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put(key, replacement);
        return new StringSubstitutor(replaceMap).replace(originalQuery);
    }

    /** --- PostgreSQL Hstore handling methods --- */

    /**
     * Converts the output of hstore_to_array(column) to a mapping of the hstore key/val pairs.
     * For example if you have an hstore value 'a=>1, b=>2', to retrieve a Map {a=1, b=2} have the
     * sql query return hstore_to_array(column) and feed the result set to this method.
     */
    public static Map<String, String> getHstoreMap(ResultSet rs, String column) throws SQLException {
        String[] hstoreArr = (String[]) rs.getArray(column).getArray();
        Map<String, String> hstoreMap = new HashMap<>();
        String key = "";
        for (int i = 0; i < hstoreArr.length; i++) {
            if (i % 2 == 0) {
                key = hstoreArr[i];
            }
            else {
                hstoreMap.put(key, hstoreArr[i]);
            }
        }
        return hstoreMap;
    }

    /**
     * Converts a hstore string into a mapping of the hstore key value pairs.
     * This method properly handles quoted values that contain commas, spaces, and special characters.
     * @param hstoreString a String in the format of "print_no"=>"S100", "session_year"=>"2015".
     *                     This string can be retrieved by calling resultSet.getString("hstore")
     *                     on the ResultSet from "SELECT 'print_no=>S100,session_year=>2015'::hstore as hstore"
     * @return A map containing all hstore key value pairs.
     */
    public static Map<String, String> hstoreStringToMap(String hstoreString) {
        Map<String, String> hstoreMap = new HashMap<>();
        
        if (hstoreString == null || hstoreString.trim().isEmpty()) {
            return hstoreMap;
        }
        
        // Parse hstore string properly handling quoted values and special characters
        int i = 0;
        while (i < hstoreString.length()) {
            // Skip whitespace
            while (i < hstoreString.length() && Character.isWhitespace(hstoreString.charAt(i))) {
                i++;
            }
            if (i >= hstoreString.length()) break;
            
            // Parse key
            String key = parseHstoreValue(hstoreString, i);
            i += getValueLength(hstoreString, i);
            
            // Skip whitespace and '=>'
            while (i < hstoreString.length() && (Character.isWhitespace(hstoreString.charAt(i)) || hstoreString.charAt(i) == '=')) {
                i++;
            }
            if (i < hstoreString.length() && hstoreString.charAt(i) == '>') {
                i++;
            }
            while (i < hstoreString.length() && Character.isWhitespace(hstoreString.charAt(i))) {
                i++;
            }
            
            // Parse value
            String value = null;
            if (i < hstoreString.length()) {
                value = parseHstoreValue(hstoreString, i);
                i += getValueLength(hstoreString, i);
            }
            
            hstoreMap.put(key, value);
            
            // Skip comma and whitespace
            while (i < hstoreString.length() && (Character.isWhitespace(hstoreString.charAt(i)) || hstoreString.charAt(i) == ',')) {
                i++;
            }
        }
        
        return hstoreMap;
    }
    
    /**
     * Helper method to parse a single hstore value (key or value) handling quotes and escaping.
     */
    private static String parseHstoreValue(String hstoreString, int startIndex) {
        int i = startIndex;
        
        // Skip leading whitespace
        while (i < hstoreString.length() && Character.isWhitespace(hstoreString.charAt(i))) {
            i++;
        }
        
        if (i >= hstoreString.length()) {
            return "";
        }
        
        // Check if value is quoted
        if (hstoreString.charAt(i) == '"') {
            // Parse quoted value
            StringBuilder sb = new StringBuilder();
            i++; // skip opening quote
            
            while (i < hstoreString.length()) {
                char c = hstoreString.charAt(i);
                if (c == '"') {
                    // Check if it's an escaped quote
                    if (i + 1 < hstoreString.length() && hstoreString.charAt(i + 1) == '"') {
                        sb.append('"');
                        i += 2;
                    } else {
                        // End of quoted string
                        break;
                    }
                } else if (c == '\\' && i + 1 < hstoreString.length()) {
                    // Handle escaped characters
                    char nextChar = hstoreString.charAt(i + 1);
                    sb.append(nextChar);
                    i += 2;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        } else {
            // Parse unquoted value
            StringBuilder sb = new StringBuilder();
            while (i < hstoreString.length()) {
                char c = hstoreString.charAt(i);
                if (c == ',' || c == '=' || (c == '>' && i > 0 && hstoreString.charAt(i-1) == '=')) {
                    break;
                }
                if (c == '\\' && i + 1 < hstoreString.length()) {
                    // Handle escaped characters
                    char nextChar = hstoreString.charAt(i + 1);
                    sb.append(nextChar);
                    i += 2;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString().trim();
        }
    }
    
    /**
     * Helper method to get the length of a parsed hstore value.
     */
    private static int getValueLength(String hstoreString, int startIndex) {
        int i = startIndex;
        
        // Skip leading whitespace
        while (i < hstoreString.length() && Character.isWhitespace(hstoreString.charAt(i))) {
            i++;
        }
        
        if (i >= hstoreString.length()) {
            return i - startIndex;
        }
        
        // Check if value is quoted
        if (hstoreString.charAt(i) == '"') {
            i++; // skip opening quote
            while (i < hstoreString.length()) {
                char c = hstoreString.charAt(i);
                if (c == '"') {
                    if (i + 1 < hstoreString.length() && hstoreString.charAt(i + 1) == '"') {
                        i += 2; // escaped quote
                    } else {
                        i++; // closing quote
                        break;
                    }
                } else if (c == '\\' && i + 1 < hstoreString.length()) {
                    i += 2; // escaped character
                } else {
                    i++;
                }
            }
        } else {
            // Unquoted value
            while (i < hstoreString.length()) {
                char c = hstoreString.charAt(i);
                if (c == ',' || c == '=' || (c == '>' && i > 0 && hstoreString.charAt(i-1) == '=')) {
                    break;
                }
                if (c == '\\' && i + 1 < hstoreString.length()) {
                    i += 2; // escaped character
                } else {
                    i++;
                }
            }
        }
        
        return i - startIndex;
    }

    /**
     * Converts the given map into the hstore string format (i.e. 'key1=>val1, key2=>val2, etc')
     */
    public static String toHstoreString(Map<String, String> hstoreMap) {
        return hstoreMap.entrySet().stream()
            .map(kv -> kv.getKey() + "=>" +
                    (kv.getValue() == null ? "NULL"
                            : !StringUtils.hasLength(kv.getValue()) ? "\"\""
                            : kv.getValue().replaceAll("([,=> ])", "\\\\$1").replaceAll("'", "''")))
            .collect(Collectors.joining(","));
    }

    /** --- Update Dao Methods --- */

    protected String getDateColumnForUpdateType(UpdateType updateType) {
        String dateColumn;
        if (updateType.equals(UpdateType.PROCESSED_DATE)) {
            dateColumn = "action_date_time";
        }
        else if (updateType.equals(UpdateType.PUBLISHED_DATE)) {
            dateColumn = "published_date_time";
        }
        else {
            throw new IllegalArgumentException("Cannot provide updates of type: " + updateType);
        }
        return dateColumn;
    }

    protected OrderBy getOrderByForUpdateType(UpdateType updateType, SortOrder sortOrder) {
        OrderBy orderBy;
        if (updateType.equals(UpdateType.PROCESSED_DATE)) {
            orderBy = new OrderBy("last_processed_date_time", sortOrder);
        }
        else if (updateType.equals(UpdateType.PUBLISHED_DATE)) {
            orderBy = new OrderBy("last_published_date_time", sortOrder, "last_processed_date_time", sortOrder);
        }
        else {
            throw new IllegalArgumentException("Cannot provide updates of type: " + updateType);
        }
        return orderBy;
    }

    /** --- Date Methods -- */

    /**
     * Read the 'column' date value from the result set and cast it to a LocalDateTime.
     * Return null if the column value is null.
     */
    public static LocalDateTime getLocalDateTimeFromRs(ResultSet rs, String column) throws SQLException {
        if (rs.getTimestamp(column) == null) return null;
        return rs.getTimestamp(column).toLocalDateTime();
    }

    /**
     * Read the 'column' date value from the result set and cast it to a LocalDate.
     * Return null if the column value is null.
     */
    public static LocalDate getLocalDateFromRs(ResultSet rs, String column) throws SQLException {
        if (rs.getDate(column) == null) return null;
        return rs.getDate(column).toLocalDate();
    }

    /**
     * Read the 'column' time value from the result set and cast it to a LocalTime.
     * Return null if the column value is null.
     */
    public static LocalTime getLocalTimeFromRs(ResultSet rs, String column) throws SQLException {
        if (rs.getTime(column) == null) return null;
        return rs.getTime(column).toLocalTime();
    }

    /**
     * Read the 'column' int value from the result set and return a new SessionYear instance.
     */
    public static SessionYear getSessionYearFromRs(ResultSet rs, String column) throws SQLException {
        return new SessionYear(rs.getInt(column));
    }

    public static String toPostgresArray(Collection<?> objects) {
        String commaSeparatedList = objects.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "{" + commaSeparatedList + "}";
    }
}