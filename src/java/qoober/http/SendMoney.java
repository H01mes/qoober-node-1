/*
 * Copyright © 2013-2016 The Qoober Core Developers.
 * Copyright © 2016-2020 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Qoober software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package qoober.http;

import qoober.Account;
import qoober.QooberException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SendMoney extends CreateTransaction {

    static final SendMoney instance = new SendMoney();

    private SendMoney() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "recipient", "amountNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws QooberException {
        long recipient = ParameterParser.getAccountId(req, "recipient", true);
        long amountNQT = ParameterParser.getAmountNQT(req);
        Account account = ParameterParser.getSenderAccount(req);
        return createTransaction(req, account, recipient, amountNQT);
    }

}
