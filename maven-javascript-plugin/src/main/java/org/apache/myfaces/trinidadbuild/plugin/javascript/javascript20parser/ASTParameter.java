/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
/* Generated By:JJTree: Do not edit this line. ASTParameter.java */

package org.apache.myfaces.trinidadbuild.plugin.javascript.javascript20parser;

public class ASTParameter extends SimpleNode
{
  public ASTParameter(int id)
  {
    super(id);
  }

  public ASTParameter(JSParser20 p, int id)
  {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(JSParser20Visitor visitor, Object data)
  {
    return visitor.visit(this, data);
  }
}
