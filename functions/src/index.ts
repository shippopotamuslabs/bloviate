import * as admin from "firebase-admin";

admin.initializeApp();

export { styleText } from "./style/styleText";
export { deleteCustomStyle, listCustomStyles, saveCustomStyle } from "./style/customStyles";
export { generateCustomStyle } from "./style/generateCustomStyle";
export { submitOutputReport } from "./report/reportOutput";
export { deleteAccount } from "./account/deleteAccount";
