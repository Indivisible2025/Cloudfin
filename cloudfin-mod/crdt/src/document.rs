//! CRDT Document 实现

use anyhow::Result;
use yrs::{Doc, ReadTxn, TextRef, Transact, XmlFragmentRef, StateVector, Update};
use yrs::updates::decoder::Decode;

pub struct CrdtDocument {
    doc: Doc,
    #[allow(dead_code)]
    name: String,
}

impl CrdtDocument {
    pub fn new(name: &str) -> Self {
        let doc = Doc::new();
        Self { doc, name: name.into() }
    }

    pub fn get_text(&self) -> TextRef {
        self.doc.get_or_insert_text("content")
    }

    pub fn get_xml(&self, name: &str) -> XmlFragmentRef {
        self.doc.get_or_insert_xml_fragment(name)
    }

    pub fn apply_update(&mut self, update: &[u8]) -> Result<()> {
        let update = Update::decode_v1(update)?;
        let mut txn = self.doc.transact_mut();
        txn.apply_update(update)?;
        Ok(())
    }

    pub fn state(&self) -> Vec<u8> {
        let txn = self.doc.transact();
        txn.encode_diff_v1(&StateVector::default())
    }
}
