# Overview of Paper "State of the Art in Zero-Knowledge Machine Learning: A Comprehensive Survey"
- [Paper from Lee et al.](https://eprint.iacr.org/2020/584)
  - proposes an efficient method for verifying the correctnes of AI
  interference services in CNNS without revealing weights
  - vCNN: Verifiable Convolutional Neural Network
    based on zk-SNARKs
  - case example: 
    - clinical decision support service via AI
    - hospital takes CT Scan and sends image to AI
    - AI Doctor gives diagnoses based on the image and returns diagnosis
    - integrity check of AI needed
    - BUT: straightforward approach to verify the result is to
      re-execute AI program, however, it is impossible
      since AI weight parameters are important IPs
      and are not available publicly