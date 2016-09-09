/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.trigger.builtin;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;

import azkaban.trigger.ConditionChecker;

public class ExpireChecker implements ConditionChecker {

  public static final String type = "ExpireChecker";

  private long expireTime;

  private final String id;

  public ExpireChecker(String id, long expireTime) {
    this.id = id;
    this.expireTime = expireTime;
  }

  @Override
  public Boolean eval() {
    return expireTime < System.currentTimeMillis();
  }

  @Override
  public void reset() {
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getType() {
    return type;
  }

  @SuppressWarnings("unchecked")
  public static ExpireChecker createFromJson(Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static ExpireChecker createFromJson(HashMap<String, Object> obj)
      throws Exception {
    Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(type)) {
      throw new Exception("Cannot create checker of " + type + " from "
          + jsonObj.get("type"));
    }
    Long expireTime = Long.valueOf((String) jsonObj.get("expireTime"));
    String id = (String) jsonObj.get("id");

    ExpireChecker checker =
        new ExpireChecker(id, expireTime);
    return checker;
  }

  @Override
  public ExpireChecker fromJson(Object obj) throws Exception {
    return createFromJson(obj);
  }

  @Override
  public Object getNum() {
    return null;
  }

  @Override
  public Object toJson() {
    Map<String, Object> jsonObj = new HashMap<String, Object>();
    jsonObj.put("type", type);
    jsonObj.put("expireTime", String.valueOf(expireTime));
    jsonObj.put("id", id);
    return jsonObj;
  }

  @Override
  public void stopChecker() {
    return;
  }

  @Override
  public void setContext(Map<String, Object> context) {
  }

  @Override
  public long getNextCheckTime() {
	return expireTime;
  }

}
