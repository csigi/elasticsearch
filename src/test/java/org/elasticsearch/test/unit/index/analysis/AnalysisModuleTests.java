/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.unit.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.*;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.elasticsearch.test.unit.index.analysis.filter1.MyFilterTokenFilterFactory;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.Set;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

/**
 *
 */
public class AnalysisModuleTests {

    @Test
    public void testSimpleConfigurationJson() {
        Settings settings = settingsBuilder().loadFromClasspath("org/elasticsearch/test/unit/index/analysis/test1.json").build();
        testSimpleConfiguration(settings);
    }

    @Test
    public void testSimpleConfigurationYaml() {
        Settings settings = settingsBuilder().loadFromClasspath("org/elasticsearch/test/unit/index/analysis/test1.yml").build();
        testSimpleConfiguration(settings);
    }
    
    @Test
    public void testDefaultFactory() {
        AnalysisService analysisService = AnalysisTestsHelper.createAnalysisServiceFromSettings(ImmutableSettings.settingsBuilder().build());
        TokenFilterFactory tokenFilter = analysisService.tokenFilter("keyword_repeat");
        Tokenizer tokenizer = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader("foo bar"));
        TokenStream stream = tokenFilter.create(tokenizer);
        assertThat(stream, instanceOf(KeywordRepeatFilter.class));
    }

    private void testSimpleConfiguration(Settings settings) {
        Index index = new Index("test");
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings), new EnvironmentModule(new Environment(settings)), new IndicesAnalysisModule()).createInjector();
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class)))
                .createChildInjector(parentInjector);

        AnalysisService analysisService = injector.getInstance(AnalysisService.class);

        Analyzer analyzer = analysisService.analyzer("custom1").analyzer();

        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
        CustomAnalyzer custom1 = (CustomAnalyzer) analyzer;
        assertThat(custom1.tokenizerFactory(), instanceOf(StandardTokenizerFactory.class));
        assertThat(custom1.tokenFilters().length, equalTo(2));

        StopTokenFilterFactory stop1 = (StopTokenFilterFactory) custom1.tokenFilters()[0];
        assertThat(stop1.stopWords().size(), equalTo(1));
        //assertThat((Iterable<char[]>) stop1.stopWords(), hasItem("test-stop".toCharArray()));

        analyzer = analysisService.analyzer("custom2").analyzer();
        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
        CustomAnalyzer custom2 = (CustomAnalyzer) analyzer;

//        HtmlStripCharFilterFactory html = (HtmlStripCharFilterFactory) custom2.charFilters()[0];
//        assertThat(html.readAheadLimit(), equalTo(HTMLStripCharFilter.DEFAULT_READ_AHEAD));
//
//        html = (HtmlStripCharFilterFactory) custom2.charFilters()[1];
//        assertThat(html.readAheadLimit(), equalTo(1024));

        // verify characters  mapping
        analyzer = analysisService.analyzer("custom5").analyzer();
        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
        CustomAnalyzer custom5 = (CustomAnalyzer) analyzer;
        assertThat(custom5.charFilters()[0], instanceOf(MappingCharFilterFactory.class));

        // verify aliases
        analyzer = analysisService.analyzer("alias1").analyzer();
        assertThat(analyzer, instanceOf(StandardAnalyzer.class));

        // check custom class name (my)
        analyzer = analysisService.analyzer("custom4").analyzer();
        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
        CustomAnalyzer custom4 = (CustomAnalyzer) analyzer;
        assertThat(custom4.tokenFilters()[0], instanceOf(MyFilterTokenFilterFactory.class));

//        // verify Czech stemmer
//        analyzer = analysisService.analyzer("czechAnalyzerWithStemmer").analyzer();
//        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
//        CustomAnalyzer czechstemmeranalyzer = (CustomAnalyzer) analyzer;
//        assertThat(czechstemmeranalyzer.tokenizerFactory(), instanceOf(StandardTokenizerFactory.class));
//        assertThat(czechstemmeranalyzer.tokenFilters().length, equalTo(4));
//        assertThat(czechstemmeranalyzer.tokenFilters()[3], instanceOf(CzechStemTokenFilterFactory.class));
//
//        // check dictionary decompounder
//        analyzer = analysisService.analyzer("decompoundingAnalyzer").analyzer();
//        assertThat(analyzer, instanceOf(CustomAnalyzer.class));
//        CustomAnalyzer dictionaryDecompounderAnalyze = (CustomAnalyzer) analyzer;
//        assertThat(dictionaryDecompounderAnalyze.tokenizerFactory(), instanceOf(StandardTokenizerFactory.class));
//        assertThat(dictionaryDecompounderAnalyze.tokenFilters().length, equalTo(1));
//        assertThat(dictionaryDecompounderAnalyze.tokenFilters()[0], instanceOf(DictionaryCompoundWordTokenFilterFactory.class));

        Set<?> wordList = Analysis.getWordSet(null, settings, "index.analysis.filter.dict_dec.word_list", Lucene.VERSION);
        MatcherAssert.assertThat(wordList.size(), equalTo(6));
//        MatcherAssert.assertThat(wordList, hasItems("donau", "dampf", "schiff", "spargel", "creme", "suppe"));
    }

    @Test
    public void testWordListPath() throws Exception {
        Environment env = new Environment(ImmutableSettings.Builder.EMPTY_SETTINGS);
        String[] words = new String[]{"donau", "dampf", "schiff", "spargel", "creme", "suppe"};

        File wordListFile = generateWordList(words);
        Settings settings = settingsBuilder().loadFromSource("index: \n  word_list_path: " + wordListFile.getAbsolutePath()).build();

        Set<?> wordList = Analysis.getWordSet(env, settings, "index.word_list", Lucene.VERSION);
        MatcherAssert.assertThat(wordList.size(), equalTo(6));
//        MatcherAssert.assertThat(wordList, hasItems(words));
    }

    private File generateWordList(String[] words) throws Exception {
        File wordListFile = File.createTempFile("wordlist", ".txt");
        wordListFile.deleteOnExit();

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(wordListFile), Charsets.UTF_8));
            for (String word : words) {
                writer.write(word);
                writer.write('\n');
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        return wordListFile;
    }

}
