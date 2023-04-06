/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author WanYi
 * @time 2021年7月31日17:43:57
 * @desc java 正则表达式兼容Posix规范表达式 工具类
 ***/
public class PosixUtis
{
    private PosixUtis() {}

    /**
     * 字母字符和数字字符
     **/
    private static final String ALNUM = "[[:alnum:]]";

    /**
     * 匹配字母
     **/
    private static final String ALPHA = "[[:alpha:]]";

    /**
     * 小写字母字符
     **/
    private static final String LOWER = "[[:lower:]]";

    /**
     * POSIX空格字符和制表符
     **/
    private static final String BLANK = "[[:blank:]]";

    /**
     * 控制字符
     **/
    private static final String CNTRL = "[[:cntrl:]]";

    /**
     * 数字字符
     **/
    private static final String DIGIT = "[[:digit:]]";

    /**
     * 十六进制字符
     **/
    private static final String XDIGIT = "[[:xdigit:]]";

    /**
     * 空白字符之外的字符
     **/
    private static final String GRAPH = "[[:graph:]]";

    /**
     * 类似[:graph:]，但包括空白字符
     **/
    private static final String PRINT = "[[:print:]]";

    /**
     * 标点符号
     **/
    private static final String PUNCT = "[[:punct:]]";

    /**
     * 大写字母字符
     **/
    private static final String UPPER = "[[:upper:]]";

    /**
     * [:word:]是附加的非POSIX的character class
     **/
    private static final String WORD = "[[:word:]]";

    /**
     * 空白字符
     **/
    private static final String SPACE = "[[:space:]]";

    /**
     * [:digit:]=[0-9]
     **/
    private static final String DIGIT_REGEX = "[0-9]";

    /**
     * [:alpha:]=[a-zA-Z]
     **/
    private static final String ALPHA_REGEX = "[a-zA-Z]";

    /**
     * [:lower:] =[a-z]
     **/
    private static final String LOWER_REGEX = "[a-z]";

    /**
     * [:upper:]=[A-Z]
     **/
    private static final String UPPER_REGEX = "[A-Z]";

    /**
     * [:alnum:]=[a-zA-Z0-9]
     **/
    private static final String ALNUM_REGEX = "[a-zA-Z0-9]";

    /**
     * [:blank:]=[ \t]
     **/
    private static final String BLANK_REGEX = "[ \\t]";

    /**
     * [:cntrl:]=[\x00-\x1F\x7F]
     **/
    private static final String CNTRL_REGEX = "[\\x00-\\x1F\\x7F]";

    /**
     * [:xdigit:]=[A-Fa-f0-9]
     **/
    private static final String XDIGIT_REGEX = "[A-Fa-f0-9]";

    /**
     * [:punct:]=[\[\]!"#$%&'()*+,./:;<=>?@\^_`{|}~-]
     **/
    private static final String PUNCT_REGEX = "[\\[\\]!\"#$%&'()*+,./:;<=>?@\\^_`{|}~-]";

    /**
     * [:print:]=[\x20-\x7E]
     **/
    private static final String PRINT_REGEX = "[\\x20-\\x7E]";

    /**
     * [:graph:]=[\x21-\x7E]
     **/
    private static final String GRAPH_REGEX = "[\\x21-\\x7E]";

    /**
     * [:word:]=[A-Za-z0-9_]
     **/
    private static final String WORD_REGEX = "[A-Za-z0-9_]";

    /**
     * [:space:]=[ \t\r\n\v\f]
     **/
    private static final String SPACE_REGEX = "[ \\t\\r\\n\\v\\f]";

    /**
     * 存储Posix正则规范表达式列表
     **/
    private static final List<String> POSIXS = initPosixs();

    private static final Map<String, String> REPLACE_POSIXS = initPosixsMap();

    /**
     * 初始化posixs列表
     *
     * @param
     * @return
     **/
    private static List<String> initPosixs()
    {
        return Arrays.asList(ALNUM, ALPHA, LOWER, BLANK, CNTRL, DIGIT, XDIGIT, GRAPH, PRINT, PUNCT, WORD, SPACE, UPPER);
    }

    /**
     * 初始化posixs正则表达式与java 正则表达式映射关联关系
     *
     * @param
     * @return
     **/
    private static Map<String, String> initPosixsMap()
    {
        Map<String, String> replacePosixs = new HashMap();
        replacePosixs.put(ALNUM, ALNUM_REGEX);
        replacePosixs.put(ALPHA, ALPHA_REGEX);
        replacePosixs.put(LOWER, LOWER_REGEX);
        replacePosixs.put(BLANK, BLANK_REGEX);
        replacePosixs.put(CNTRL, CNTRL_REGEX);
        replacePosixs.put(DIGIT, DIGIT_REGEX);
        replacePosixs.put(XDIGIT, XDIGIT_REGEX);
        replacePosixs.put(GRAPH, GRAPH_REGEX);
        replacePosixs.put(PRINT, PRINT_REGEX);
        replacePosixs.put(PUNCT, PUNCT_REGEX);
        replacePosixs.put(SPACE, SPACE_REGEX);
        replacePosixs.put(UPPER, UPPER_REGEX);
        replacePosixs.put(WORD, WORD_REGEX);
        return replacePosixs;
    }

    /**
     * 替换正则表达式中匹配到的Posix范围表达式
     *
     * @param regexp
     * @return string
     **/
    public static String replRegExp(String regexp)
    {
        for (String reg : POSIXS) {
            //java转义特殊字符串[->\\[ ]->\\]
            String escapeReg = reg.replaceAll("\\[", Matcher.quoteReplacement("\\[")).replaceAll("\\]", Matcher.quoteReplacement("\\]"));
            //忽略Posix正则表达式大小写
            Pattern p = Pattern.compile(".*".concat(escapeReg).concat(".*"), Pattern.CASE_INSENSITIVE);
            if (p.matcher(regexp).matches()) {
                //替换表达式忽略大小写
                regexp = regexp.replaceAll("(?i)".concat(escapeReg), REPLACE_POSIXS.get(reg));
            }
        }
        //兼容MaxCompute---\\转义问题
        return regexp.replaceAll(Matcher.quoteReplacement("\\\\"), Matcher.quoteReplacement("\\"));
    }
}
