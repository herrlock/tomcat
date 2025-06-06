/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.compiler;

import java.io.File;
import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestValidator extends TomcatBaseTest {

    @Test
    public void testBug47331() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        int rc = getUrl("http://localhost:" + getPort() + "/test/bug47331.jsp", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testTldVersions22() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.2");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>${'00-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'02-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'04-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'06-hello world'}</p>") > 0);
    }

    @Test
    public void testTldVersions23() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.3");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>${'00-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'02-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'04-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'06-hello world'}</p>") > 0);
    }

    @Test
    public void testTldVersions24() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.4");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions25() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.5");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions30() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions31() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions40() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-4.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions50() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-5.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions60() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions61() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>09-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions62() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.2");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>09-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>10-hello world</p>") > 0);
    }

    public static class Echo extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String echo = null;

        public void setEcho(String echo) {
            this.echo = echo;
        }

        public String getEcho() {
            return echo;
        }

        @Override
        public int doStartTag() throws JspException {
            try {
                pageContext.getOut().print("<p>" + echo + "</p>");
            } catch (IOException e) {
                pageContext.getServletContext().log("Tag (Echo21) failure", e);
            }
            return super.doStartTag();
        }
    }
}
