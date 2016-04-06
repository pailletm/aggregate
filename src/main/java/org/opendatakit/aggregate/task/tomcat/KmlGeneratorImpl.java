/*
 * Copyright (C) 2010 University of Washington
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
package org.opendatakit.aggregate.task.tomcat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opendatakit.aggregate.client.form.KmlSelection;
import org.opendatakit.aggregate.constants.BeanDefs;
import org.opendatakit.aggregate.exception.ODKFormNotFoundException;
import org.opendatakit.aggregate.form.IForm;
import org.opendatakit.aggregate.form.PersistentResults;
import org.opendatakit.aggregate.submission.SubmissionKey;
import org.opendatakit.aggregate.task.KmlGenerator;
import org.opendatakit.aggregate.task.KmlWorkerImpl;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.web.CallingContext;

/**
 * This is a singleton bean. It cannot have any per-request state. It uses a
 * static inner class to encapsulate the per-request state of a running
 * background task.
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class KmlGeneratorImpl implements KmlGenerator {

  static class KmlRunner implements Runnable {
    final KmlWorkerImpl impl;

    public KmlRunner(IForm form, SubmissionKey persistentResultsKey, long attemptCount,
        List<KmlSelection> kmlElementsToInclude, CallingContext cc) {

      impl = new KmlWorkerImpl(form, persistentResultsKey, attemptCount, kmlElementsToInclude, cc);
    }

    @Override
    public void run() {
      impl.generateKml();
    }
  }

  @Override
  public void createKmlTask(IForm form, PersistentResults persistentResults, long attemptCount,
      CallingContext cc) throws ODKDatastoreException, ODKFormNotFoundException {

    List<KmlSelection> kmlElementsToInclude = new ArrayList<KmlSelection>();

    Map<String, String> params = persistentResults.getRequestParameters();
    String encodedString = params.get(KmlGenerator.KML_SELECTIONS_KEY);
    if (encodedString != null) {
      String[] kmlSelectionsEncodedStrings = encodedString
          .split(KmlGenerator.KML_SELECTIONS_DELIMITER);
      for (String kmlSelectionEncodedString : kmlSelectionsEncodedStrings) {
        KmlSelection kmlElement = KmlSelection
            .createKmlSelectionFromEncodedString(kmlSelectionEncodedString);
        if (kmlElement != null) {
          kmlElementsToInclude.add(kmlElement);
        }
      }
    }

    WatchdogImpl wd = (WatchdogImpl) cc.getBean(BeanDefs.WATCHDOG);
    // use watchdog's calling context in runner...
    KmlRunner runner = new KmlRunner(form, persistentResults.getSubmissionKey(), attemptCount,
        kmlElementsToInclude, wd.getCallingContext());
    AggregrateThreadExecutor exec = AggregrateThreadExecutor.getAggregateThreadExecutor();
    exec.execute(runner);
  }
}
